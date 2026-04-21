mvn source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pboms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args