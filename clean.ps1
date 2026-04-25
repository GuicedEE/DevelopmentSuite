mvn clean `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pguicedee-boms,jwebmp-boms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args