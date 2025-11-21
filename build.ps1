mvn clean source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pguicedee,services,entityassist,jwebmp,activity-master" `
  -T 8 `
  @args