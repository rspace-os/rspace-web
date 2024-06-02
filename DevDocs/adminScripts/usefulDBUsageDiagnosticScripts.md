# Useful diagnostic DB queries

## Document and Workspace usage:

### General counts

```mysql
select count(*) as 'Number of Base Records' from BaseRecord;
select count(*) as 'Number of Structured Documents' from StructuredDocument;
select count(*) as 'Number of Notebooks' from Notebook;
```

#### Top 10 users with most BaseRecords

```mysql
select createdBy as User, count(*) as NumberOfBaseRecords from BaseRecord \
group by createdBy order by NumberOfBaseRecords desc limit 10;
```

#### Top 10 users with most Structured Documents

```mysql
select createdBy as User, count(*) as NumberOfStructuredDocuments from BaseRecord br \
inner join StructuredDocument sd on br.id = sd.id \
group by createdBy order by NumberOfStructuredDocuments desc limit 10;
```

#### Top 10 notebooks with most entries

```mysql
select createdBy as User, count(*) as NumberOfEntries, br.id as NotebookId \
from RecordToFolder rtf \
inner join Notebook nb on rtf.folder_id = nb.id \
inner join BaseRecord br on nb.id = br.id \
where rtf.recordInFolderDeleted = (0) \
group by rtf.folder_id order by NumberOfEntries desc limit 10;
```

#### Top 10 longest documents (based on Field count)  

```mysql
select createdBy User, count(*) as NumberOfFields, br.id DocumentId from Field f\
inner join BaseRecord br on br.id = f.structuredDocument_id\
group by f.structuredDocument_id \
order by NumberOfFields desc\
limit 40;
```

#### Group record counts into buckets to analyse record count distribution

```mysql
select floor(q.total / 20)*20 floor, count(*) as count from (select owner_id, count(*) as total from BaseRecord group by owner_id)q  group by 1;
```

#### Field structure of the longest document

```mysql
select f.id as FieldId, f.DTYPE as FieldType from Field f \
where structuredDocument_id = (select structuredDocument_id from \
(select structuredDocument_id, count(*) as NumberOfFields from Field \
  where structuredDocument_id is not NULL group by structuredDocument_id \
    order by NumberOfFields desc limit 1) as LongestDoc);
```

### Record-related features:

Gets number of chem elements per user.
```mysql
select u.username, u.email, count(u.email) as ChemsPerUser from User  u inner join BaseRecord br\
 on br.owner_id=u.id\
 inner join RSChemElement chem on chem.record_id=br.id\
 group by u.id order by ChemsPerUser desc;
```

Snippets per user.
```mysql
select u.username, u.email, count(u.email) as SnippetsPerUser from User  u inner join BaseRecord br\
 on br.owner_id=u.id inner join Snippet chem on chem.id=br.id\
 group by u.id\
 order by SnippetsPerUser desc;
```

## Form usage:

Forms that have a single text field:
```mysql
select f.id, f.name, count(ff.id) as fieldCount from RSForm f\
  inner join FieldForm ff on f.id = ff.form_id\
  inner join User u on u.id = f.owner_id where bin(current) = 1 and ff.type = 'TEXT'\
  group by ff.form_id  having fieldCount = 1\
  order by fieldCount desc;
```

Documents created from Singletext-field forms:
```mysql
select count(*) from StructuredDocument sd inner join BaseRecord br on br.id=sd.id\
  where sd.form_id in (select t.id from (select f.id, f.name, count(ff.id) as fieldCount from RSForm f\
   inner join FieldForm ff on f.id= ff.form_id where bin(current) = 1 and ff.type = 'TEXT'\
   group by ff.form_id  having fieldCount = 1)t)\
and br.owner_id not in (select id from User where email like '%researchspace.com');
```

Forms that have more than one text field:
```mysql
select f2.id, f2.name from RSForm f2 where f2.id not in\
 (select t.fid from (select f.id fid, count(ff.id) as fieldCount\
   from RSForm f inner join FieldForm ff on f.id= ff.form_id\
   where bin(current) = 1 and ff.type = 'TEXT' group by ff.form_id having fieldCount = 1 )t)\
 and bin(current) = 1;
```

Documents created from non-single text-field forms:
```mysql
select count(*) from StructuredDocument sd inner join BaseRecord br on br.id=sd.id\
   where sd.form_id in (select f2.id from RSForm f2 where f2.id not in\
          (select t.fid from (select f.id fid, count(ff.id) as fieldCount\
          from RSForm f inner join FieldForm ff on f.id = ff.form_id\
          where bin(current) = 1 and ff.type = 'TEXT'\
          group by ff.form_id having fieldCount =1 )t)\
    and bin(current) = 1)\
    and br.owner_id not in (select id from User where email like '%researchspace.com');
```

## App usage

### Slack usage

Two queries. Gets username and email of users who have registered >= 1 Slack channel
```mysql
select @appIdentifier := id from AppConfigElementDescriptor \
 where descriptor_id = (select id from PropertyDescriptor where name ='SLACK_WEBHOOK_URL');
 
select u.username, u.email from User u where  u.id in \
  (select user_id from UserAppConfig uac  where uac.id in \
     (select userAppConfig_id from AppConfigElementSet aces where aces.id in \
        (select appConfigElementSet_id from AppConfigElement where appConfigElementDescriptor_id = @appIdentifier)));
 ```             

### Figshare usage

```mysql
select u.username, u.email from User u inner join UserConnection uc on u.username=uc.userId\
 where providerId='figshare';
```

## File Usage

### By group (1.38)
    
```mysql
select g.displayName,  sum(fp.fileSize) as files \
from rsGroup g \
inner join UserGroup ug on g.id = ug.group_id \
inner join User u on u.id = ug.user_id \
inner join FileProperty fp on fp.fileOwner=u.username \
group by g.displayName;
```

### Total file usage 

```mysql
select sum(files) as total_file_usage from (select g.displayName,  sum(fp.fileSize) as files \
from rsGroup g \
inner join UserGroup ug on g.id = ug.group_id \
inner join User u on u.id = ug.user_id \
inner join FileProperty fp on fp.fileOwner = u.username \
group by g.displayName) byGroup;
```

## Users
 
### Ordered by email suffix ( community)
 
```mysql
select  substr(email, instr(email, '@')+1) as emailHost, email from User order by emailHost  asc;
```
   
### Last login date buckets

```mysql
select q.rangex, count(q.rangex) from (select  case when DATE_SUB(CURDATE(),INTERVAL 3 YEAR) > lastLogin then 3 when DATE_SUB(CURDATE(),INTERVAL 2 YEAR) > lastLogin then 2 when DATE_SUB(CURDATE(),INTERVAL 1 YEAR) > lastLogin then 1  else 0 end as rangex from User )q group by q.rangex;
```

### Users who last logged in during a date bucket.

```mysql
select q.email, q.username, q.lastLogin from (select  case when DATE_SUB(CURDATE(),INTERVAL 3 YEAR) > lastLogin then 3 when DATE_SUB(CURDATE(),INTERVAL 2 YEAR) > lastLogin then 2 when DATE_SUB(CURDATE(),INTERVAL 1 YEAR) > lastLogin then 1  else 0 end as rangex, email, username, lastLogin from User )q where q.rangex = 1;
```

### All users and their groups:

```mysql
select u.username, concat(u.first_name, " ", u.last_name) as name, g.displayName as groupName, if(ug.roleInGroup = 1, "PI", "USER") as roleInGroup from User u left join UserGroup ug on u.id=ug.user_id left join rsGroup g  on g.id=ug.group_id order by groupName asc;
```

### All groups and their members (ignoring users not in groups):

```mysql
select g.displayName as groupName, u.username, concat(u.first_name, " ", u.last_name) as name, if(ug.roleInGroup = 1, "PI", "USER") as groupRole from rsGroup g join UserGroup ug on g.id=ug.group_id join User u on u.id=ug.user_id;
```
