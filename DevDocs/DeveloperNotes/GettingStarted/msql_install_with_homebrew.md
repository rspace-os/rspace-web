##Install MYSQL with Homebrew on OSX Monterey

1. `brew install mysql@5.7`   (see https://formulae.brew.sh/formula/mysql@5.7

2. start and stop:
   `brew services start mysql@5.7` (or ‘stop’).

3. edit .zshrc with path to mysql install so that scripts run.  e.g 
   Scripts are in /opt/homebrew/Cellar/mysql@5.7/5.7.36/bin/
```
export PATH="/opt/homebrew/Cellar/mysql@5.7/5.7.36/bin:$PATH"
```
Then source ~/.zshrc in current shell.

4. run the following to secure the install :
/opt/homebrew/Cellar/mysql@5.7/5.7.36/bin/mysql_secure_installation 
(if you set the path, then its just `mysql_secure_installation`)

(remove test db and all anonymous access etc and also set the root password).

5. make a file '/etc/mysql/my.cnf' as one will not exist.
   sudo mkdir /etc/mysql
   sudo vi /etc/mysql/my.cnf

Paste in :
```
[mysqld]
    bind-address = 127.0.0.1
sql_mode = STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION
```
6. Restart mysql
7. connect to mysql from command line : `mysql -u root -p`
8. Double check that the sql_mode edit worked: `SELECT @@sql_mode;` from mysql command prompt will not show 'ONLY_FULL_GROUP_BY'
9. Proceed with `GettingStarted.md #### RSpace table setup`