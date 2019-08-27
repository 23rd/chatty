( gradlew windowsZip -PjavapackagerPath="c:\java8\bin\javapackager.exe"
gradlew innosetup -PinnosetupPath="c:\Program Files (x86)\Inno Setup 6\ISCC.exe"
gradlew innosetupStandalone -PinnosetupPath="c:\Program Files (x86)\Inno Setup 6\ISCC.exe" -PjavapackagerPath="c:\java8\bin\javapackager.exe")