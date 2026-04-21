mvn source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=false" `
  "-Pboms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args