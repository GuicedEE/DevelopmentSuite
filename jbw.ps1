mvn source:jar install `
  "-DskipTests" `
  "-Pjwebmp" `
  -T 8 `
  @args