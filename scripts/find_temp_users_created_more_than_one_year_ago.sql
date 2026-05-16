SELECT id
FROM User
WHERE User.tempAccount = true AND creationDate < DATE_SUB(NOW(), INTERVAL 1 YEAR);