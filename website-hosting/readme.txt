# Everything in the public folder is deployed to the website.
#

# First, you might need to update the firebase tools
sudo npm i -g firebase-tools@latest

# Maybe need to update node
npm install npm@latest -g

# maybe update npm
sudo npm install -g npm

# to connect (opens a webbrowser)
firebase login

# to upload the files to the website
firebase deploy

# If you get a permission error (login again and retry deploy)
firebase logout
firebase login --reauth

# Test the asset links file using uri
# NOTE: all certificate hashcode must be UPPERCASE (else verification fails)!
# https://digitalassetlinks.googleapis.com/v1/statements:list?source.web.site=https://api-8938561204297001672-604498.firebaseapp.com&relation=delegate_permission/common.handle_all_urls

# this will list all details (if it says ask and not always for the uri the it isn't verified)
#adb shell dumpsys package d
# test opening the link on the app
# adb shell am start -a android.intent.action.VIEW -c android.intent.category.BROWSABLE -d "https://api-8938561204297001672-604498.firebaseapp.com/config"

#NOTE:
# My upload certificate (this isn't used by any users' copies but if I sign the debug version for my phone it uses this
#32:24:51:EE:D7:65:02:07:FF:EF:C0:79:7B:1E:19:03:5B:5E:76:E9:CB:22:66:D2:27:C9:59:53:C4:E8:D5:6D
# the actual signing certificate.
# 95:E7:F9:45:6E:C6:C9:C5:8F:92:8A:A1:D7:68:CF:CE:A6:B0:82:EA:B7:2A:6D:5E:6C:8B:04:BF:0D:CE:5B:FD