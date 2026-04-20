mvn source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=false" `
  "-Pguicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args