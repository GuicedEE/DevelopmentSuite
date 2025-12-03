mvn deploy `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pguicedee,services,entityassist,jwebmp" `
   `
  @args