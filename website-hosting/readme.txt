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
firebase login