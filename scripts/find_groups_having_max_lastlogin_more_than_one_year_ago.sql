# users who are temp users can be invited by sysadmin into a group but they dont actually join until they log in and accept
# grpups formed solely of such users (invited users who have never logged in) dont show up in the rsGroup table
# Therefore we can conclude that any group with max(lastlogin) < DATE_SUB(NOW(), INTERVAL 1 YEAR) can be safely deleted as no logged in users
# will have seen any of the groups data anyway
SELECT ug.group_id
FROM   UserGroup ug
           JOIN   User u ON u.id = ug.user_id
GROUP BY ug.group_id
HAVING MAX(u.lastLogin) < DATE_SUB(NOW(), INTERVAL 1 YEAR);