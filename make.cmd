echo off
rem delete class file just in case there are any left over.
del *.class
setlocal
set classpath=commons-codec-1.4.jar;%classpath%
rem javac -version 2> version.txt
rem findstr /C:"javac 1.6" version.txt
rem if %errorlevel% neq 0 goto :VersionError
javac CmdLineParser.java
if %errorlevel% neq 0 goto Error
javac PDStack.java
if %errorlevel% neq 0 goto Error
javac Base64.java
if %errorlevel% neq 0 goto Error
javac JTClipper.java
if %errorlevel% neq 0 goto Exit
jar -xvf commons-codec-1.4.jar
if %errorlevel% neq 0 goto Error
jar -cvfm JTClipper.jar JTClipperManifest.mf *.class images org
if %errorlevel% neq 0 goto Error
if %errorlevel% equ 0 goto Exit
:VersionError
echo Make failed. Java version must be 1.6.X
:Error
pause
:Exit
rem del version.txt
rem we don't need the classes any more.
del *.class
endlocal

