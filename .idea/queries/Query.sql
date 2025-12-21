drop view received_barcodes;

CREATE OR REPLACE FUNCTION update_varchar_to_uuid()
    RETURNS void AS
$$
DECLARE
    v_table_name       TEXT;
    v_column_name      TEXT;
    v_schema_name      TEXT;
    fk_table_name      TEXT;
    fk_schema_name     TEXT;
    fk_column_name     TEXT;
    foreign_key_name   TEXT;
    fk_drop_query      TEXT;
    alter_column_query TEXT;
BEGIN
    -- Loop through tables with VARCHAR(36) columns in specified schemas
    FOR v_schema_name, v_table_name, v_column_name IN
        SELECT c.table_schema,
               c.table_name,
               c.column_name
        FROM information_schema.columns c
        WHERE c.data_type = 'character varying'
          AND c.character_maximum_length = 36
          AND c.table_schema NOT IN ('postgres', 'pg_catalog', 'information_schema')
        LOOP
            BEGIN
                -- Process foreign keys
                FOR foreign_key_name, fk_table_name, fk_schema_name, fk_column_name IN
                    SELECT tc.constraint_name,
                           kcu.table_name   AS fk_table,
                           kcu.table_schema AS fk_schema,
                           kcu.column_name  AS fk_column
                    FROM information_schema.table_constraints tc
                             JOIN information_schema.key_column_usage kcu
                                  ON tc.constraint_name = kcu.constraint_name
                                      AND tc.table_schema = kcu.table_schema
                    WHERE tc.constraint_type = 'FOREIGN KEY'
                      AND kcu.table_name = v_table_name
                      AND kcu.column_name = v_column_name
                      AND kcu.table_schema = v_schema_name
                    LOOP
                        BEGIN
                            fk_drop_query := format('ALTER TABLE %I.%I DROP CONSTRAINT IF EXISTS %I;',
                                                    fk_schema_name, fk_table_name, foreign_key_name);
                            RAISE NOTICE 'FOREIGN KEY DROP QUERY: %', fk_drop_query;
                            EXECUTE fk_drop_query;
                        EXCEPTION WHEN OTHERS THEN
                            RAISE WARNING 'Error dropping foreign key for %.%: %', v_schema_name, v_table_name, SQLERRM;
                        -- Continue with next foreign key
                        END;
                    END LOOP;

                -- Try to alter the column
                alter_column_query := format('ALTER TABLE %I.%I ALTER COLUMN %I TYPE UUID USING %I::UUID;',
                                             v_schema_name, v_table_name, v_column_name, v_column_name);
                RAISE NOTICE '%', alter_column_query;
                EXECUTE alter_column_query;

                RAISE NOTICE 'Successfully converted column %.%.%', v_schema_name, v_table_name, v_column_name;

            EXCEPTION WHEN OTHERS THEN
                -- Log the error but continue with next column
                RAISE WARNING 'Error converting column %.%.%: %',
                    v_schema_name, v_table_name, v_column_name, SQLERRM;
            END;
        END LOOP;

    RAISE NOTICE 'Conversion process completed';
EXCEPTION WHEN OTHERS THEN
    RAISE WARNING 'Unexpected error in main process: %', SQLERRM;
END;
$$ LANGUAGE plpgsql;

SELECT public.update_varchar_to_uuid();






create view public.received_barcodes as
SELECT DISTINCT exet.value
   FROM event.event e
     JOIN event.eventxeventtype exet ON e.eventid = exet.eventid
     JOIN event.eventtype et ON et.eventtypeid = exet.eventtypeid
  WHERE et.eventtypedesc = 'BarcodeReceived'