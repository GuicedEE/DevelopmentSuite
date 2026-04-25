mvn source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=false" `
  "-Pguicedee-boms,jwebmp-boms,guicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args