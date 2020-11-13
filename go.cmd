rem Applicaiton requres JDK 6.x or greater.
rem path=C:\Program Files\Java\jdk1.6.0\bin;%path%
setlocal
set classpath=.;commons-codec-1.4.jar;%classpath%
java -Xmx256m -jar JTClipper.jar -d %1 %2 %3 %4 %5 %6 %7 %8
rem start /B javaw -Xmx128m -jar JClipper.jar %1 %2 %3 %4 %5 %6 %7 %8
endlocal

