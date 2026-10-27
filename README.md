Download Arranger
A small Java automation tool I built for myself, as a small AI video content creator. Exe version included — you can run it without building the code yourself.
Why I built this
I make one AI video every day for my channel.
For every video, I generate many clips and download them. That is where the problem started:
The downloaded clips had messy names, so I could not tell which clip was made on which day.
The clips were not in order, so when I pasted them into my editor app to cut and combine them, I had to check every clip, rename every clip, and arrange them manually.
That renaming and arranging took a lot of time, every single day, during my animation work.
So I built this small automation for myself, to solve my own problem:
I do not have to worry about which work I did on which day.
I do not have to rename clips manually anymore.
My clips are already in order, ready to drop into the editor.
What it does
It watches the download folders I choose.
When a download finishes, it checks for a folder with today's date.
If the date folder does not exist, it creates it.
If it already exists, it uses it.
It moves the file into that date folder.
It renames the file automatically, starting from 01, then 02, 03, and so on.
Example:
W:\1 anuj32737\2026-10-27\01.mp4
W:\1 anuj32737\2026-10-27\02.mp4
W:\1 anuj32737\2026-10-27\03.mp4

W:\1 anuj272727\2026-10-27\01.mp4
W:\1 anuj272727\2026-10-27\02.mp4
Numbering is separate for each folder and resets every day, because each day gets its own folder.
Now when I open my editor, I just import 01, 02, 03 in order and start cutting and combining. No confusion, no manual renaming.
Features (Step 1)
Watches multiple folders, not just the default Downloads folder
Creates a YYYY-MM-DD folder for today automatically
Auto-renames downloads to 01, 02, 03...
Continues numbering correctly after restart, by scanning the highest existing number
Waits for downloads to finish, ignores .crdownload / .part / .tmp files
History log: date, time, watch folder, original name, new name, new path
Pause / Resume, Organize Now, Open Today's Folder
System tray app
Tech
Language: Java
UI: Swing (built into the JDK, no extra install)
File watching: Java WatchService
JDK: 17
Built in VS Code on Windows
My folders
Project folder: E:\Download Arranger
Watch folders:
W:\1 anuj32737
W:\1 anuj272727
You can change these in the app settings to your own download folders.
Run the exe version (no building needed)
If you just want to use it, download the exe version from Releases:
Download and unzip the full DownloadArranger app folder from dist.
Important: do not copy only the .exe file alone — it needs the other files in that folder to run.
Double-click DownloadArranger.exe inside dist\DownloadArranger.
The app opens, watches your folders, and starts arranging downloads automatically.
Project structure
src\com\downloadarranger\
  Main.java
  AppConfig.java
  FileOrganizer.java
  FolderWatchService.java
  HistoryLogger.java
How to run from code
Open the project in VS Code, folder E:\Download Arranger.
Make sure Extension Pack for Java is installed.
Run Main.java.
Test first with a dummy file, not an important clip.
Add your own download folders with Add Watch Folder.
How to build the exe yourself (Windows, JDK 17.0.12)
From PowerShell, inside E:\Download Arranger:
javac -d bin src/com/downloadarranger/*.java
java -cp bin com.downloadarranger.Main
jar --create --file DownloadArranger.jar --main-class com.downloadarranger.Main -C bin .
jpackage --name "DownloadArranger" --input . --main-jar DownloadArranger.jar --main-class com.downloadarranger.Main --type app-image --dest dist
Note: on my PC, jar was not found at first because the JDK bin folder was not in PATH. I fixed it with the JDK at E:\java sdk 17\bin, for example:
$jdkBin = "E:\java sdk 17\bin"
$env:Path = "$jdkBin;" + $env:Path
After jpackage, the exe is in dist\DownloadArranger\DownloadArranger.exe, with its runtime files next to it.
This is a personal tool I made for my own daily video workflow. Feel free to use it or change it for your own workflow.