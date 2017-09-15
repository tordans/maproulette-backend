#!/bin/bash

timestamp=$(($(date +'%s * 1000 + %-N / 1000000')))
# Execute psql query and output data to file
echo "Executing sql..."
ssh maproulette@maproulette.org 'bash -s' < /home/mcuthbert/scripts/query.sh

echo "copying file from docker instance..."
# Copy file from postgres docker container to local server
echo 'docker cp mr2-postgis:rawdata.out .' | ssh maproulette@maproulette.org 'bash -s'

echo "copying file from maproulette.org..."
# Copy file from maproulette.org to local
scp maproulette@maproulette.org:/home/maproulette/rawdata.out /home/mcuthbert/

echo "removing data copies on servers..."
# remove old copies of the data
ssh maproulette@maproulette.org "rm rawdata.out"
echo 'docker exec -i mr2-postgis bash -c "rm rawdata.out"' | ssh maproulette@maproulette.org 'bash -s'

echo "emailing data..."
# email data to Kelsey at kreinking@apple.com
echo "MR2 Public data from Last 24 hours." | mail -s "Public MR2 Raw Data" -a /home/mcuthbert/rawdata.out -b mcuthbert@apple.com -c gkoser@apple.com kreinking@apple.com
#echo "MR2 Public data from Last 24 hours." | mail -s "Public MR2 Raw Data" -a /home/mcuthbert/rawdata.out mcuthbert@apple.com
mv /home/mcuthbert/rawdata.out /home/mcuthbert/RAWDATA_BACKUPS/rawdata_$timestamp.out
