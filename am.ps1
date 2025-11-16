mvn clean source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pactivity-master" `
  -T 8 `
  @args