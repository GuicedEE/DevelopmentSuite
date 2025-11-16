mvn clean source:jar install `
  "-DskipTests" `
  "-Dmaven.javadoc.skip=true" `
  "-Pjwebmp" `
  -T 8 `
  @args