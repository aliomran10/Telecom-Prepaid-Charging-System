#!/bin/bash

# Get the current directory path
PROJECT_DIR=$(pwd)

echo "Starting MSC Server..."
# Launch MSC App in a separate terminal window
gnome-terminal --title="MSC Server" -- bash -c "cd '$PROJECT_DIR/msc-app' && mvn exec:java -Dexec.mainClass='com.telecom.msc.MSCServer'; exec bash"

# Wait for 4 seconds to ensure the server is up before the mobile app connects
sleep 4

echo "Starting Mobile App..."
# Launch Mobile App in a separate terminal window and pass the MSISDN argument
gnome-terminal --title="Mobile App" -- bash -c "cd '$PROJECT_DIR/mobile-app' && mvn exec:java -Dexec.mainClass='com.telecom.mobile.MobileApp' -Dexec.args='01223456789'; exec bash"

echo "Done! MSC and Mobile apps are running in separate windows."
