import re

file = open('ch.txt','r');
str = file.readlines()[1];
sub = str[45:48];
print(sub);

path = "src/chatty/Chatty.java";
chatty = open(path, 'r');
line = chatty.read();
chatty.close();

if not line:
	exit(1);

chatty = open(path, 'w');
line = re.sub(
	r"public static final String VERSION = \"0.10.0.[0-9]+", 
	"public static final String VERSION = \"0.10.0.%s" % sub, 
	line
);
#print(line)
chatty.write(line);
chatty.close();