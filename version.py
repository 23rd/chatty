import re

file = open('ch.txt','r');
str = file.readlines()[1];
sub = str[45:48];
print(sub);

partVersion = '0.16.0';
finalVersion = f"{partVersion}.{sub}";

path = "src/chatty/Chatty.java";
chatty = open(path, 'r');
line = chatty.read();
chatty.close();

if not line:
	exit(1);

chatty = open(path, 'w');
line = re.sub(
	rf"public static final String VERSION = \"{partVersion}.[0-9]+",
	f"public static final String VERSION = \"{finalVersion}",
	line
);
#print(line)
chatty.write(line);
chatty.close();

open('final_version.txt', 'w').write(finalVersion);
