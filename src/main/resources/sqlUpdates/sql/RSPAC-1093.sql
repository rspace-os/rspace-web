insert into RSPAC1093PermissionsToInvestigate 
select distinct br.id, br.name, br.createdBy, br.owner_id  
  from BaseRecord br
  inner join  (select u.id as subjectId, u2.username as memberName, 
       u2.id as memberId, g.uniqueName as grpName  from User u 
       inner join  UserGroup ug on u.id= ug.user_id
       inner join rsGroup g on g.id= ug.group_id
       inner join UserGroup ug2 on ug2.group_id=g.id
       inner join User u2 on ug2.user_id=u2.id
       where u.id != u2.id
       order by u.id) tmp
  on tmp.subjectId=br.owner_id
  where (br.acl like concat ('%', tmp.memberName, '%') or br.acl like concat('%', tmp.grpName, '%'))
   and br.type like ('%NORMAL%')
   and br.id not in (select shared_id from RecordGroupSharing)
   and br.deleted=0
   and br.id not in (select br.id from BaseRecord br inner join RecordToFolder rtf 
                         on rtf.record_id=br.id  inner join Notebook nb
                         on rtf.folder_id=nb.id inner join RecordGroupSharing rgs2
                         on rgs2.shared_id=nb.id) order by br.id asc;