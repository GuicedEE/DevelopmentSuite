create view public.received_barcodes as
SELECT DISTINCT exet.value
   FROM event.event e
     JOIN event.eventxeventtype exet ON e.eventid = exet.eventid
     JOIN event.eventtype et ON et.eventtypeid = exet.eventtypeid
  WHERE et.eventtypedesc = 'BarcodeReceived'