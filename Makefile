all:
	javac Bf_compiler.java
	echo '#!/bin/bash' > bf_compiler
	echo 'java Bf_compiler "$$@"' >> bf_compiler
	chmod +x bf_compiler

clean:
	rm -f *.class bf_compiler
