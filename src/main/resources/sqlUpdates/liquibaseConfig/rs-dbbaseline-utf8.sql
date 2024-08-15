/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `AbstractUserOrGroupImpl_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AbstractUserOrGroupImpl_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK69918B80DF74E053` (`REV`),
  CONSTRAINT `FK69918B80DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `AbstractUserOrGroupImpl_permissionStrings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AbstractUserOrGroupImpl_permissionStrings` (
  `AbstractUserOrGroupImpl_id` bigint(20) NOT NULL,
  `permissionStrings` varchar(255) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `AbstractUserOrGroupImpl_permissionStrings_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AbstractUserOrGroupImpl_permissionStrings_AUD` (
  `REV` int(11) NOT NULL,
  `AbstractUserOrGroupImpl_id` bigint(20) NOT NULL,
  `permissionStrings` varchar(255) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  KEY `FK7FD66AF4DF74E053` (`REV`),
  CONSTRAINT `FK7FD66AF4DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ArchivalCheckSum`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ArchivalCheckSum` (
  `uid` varchar(50) NOT NULL,
  `algorithm` varchar(255) DEFAULT NULL,
  `archivalDate` bigint(20) NOT NULL,
  `checkSum` bigint(20) NOT NULL,
  `zipName` varchar(255) DEFAULT NULL,
  `zipSize` bigint(20) NOT NULL,
  PRIMARY KEY (`uid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BaseRecord`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BaseRecord` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `deleted` bit(1) NOT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime NOT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `invisible` bit(1) NOT NULL,
  `lineage` longtext,
  `acl` varchar(2500) DEFAULT NULL,
  `signed` bit(1) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `typex` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) NOT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `isDeleted` (`deleted`),
  KEY `FK43851AA24A5647A6` (`owner_id`),
  CONSTRAINT `FK43851AA24A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=124 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BaseRecord_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BaseRecord_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `invisible` bit(1) DEFAULT NULL,
  `lineage` longtext,
  `acl` varchar(2500) DEFAULT NULL,
  `signed` bit(1) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `typex` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK707F2773DF74E053` (`REV`),
  CONSTRAINT `FK707F2773DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ChoiceFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ChoiceFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `choiceOptions` varchar(1000) DEFAULT NULL,
  `defaultChoiceOption` varchar(255) DEFAULT NULL,
  `multipleChoice` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA449173541d` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA449173541d` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ChoiceFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ChoiceFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `choiceOptions` varchar(1000) DEFAULT NULL,
  `defaultChoiceOption` varchar(255) DEFAULT NULL,
  `multipleChoice` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053416e2b6e` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053416e2b6e` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `CollabGroupCreationTracker`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CollabGroupCreationTracker` (
  `id` bigint(20) NOT NULL,
  `initialGrpName` varchar(255) DEFAULT NULL,
  `numInvitations` smallint(6) DEFAULT NULL,
  `numReplies` smallint(6) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  `mor_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `mor_id` (`mor_id`),
  KEY `FKA91062A71CC96626` (`group_id`),
  CONSTRAINT `FKA91062A71CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `CommunicationTarget`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CommunicationTarget` (
  `id` bigint(20) NOT NULL,
  `lastStatusUpdate` datetime DEFAULT NULL,
  `lastStatusUpdateMessage` varchar(255) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` char(1) DEFAULT NULL,
  `communication_id` bigint(20) NOT NULL,
  `recipient_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKA4C03CE73292C680` (`recipient_id`),
  CONSTRAINT `FKA4C03CE73292C680` FOREIGN KEY (`recipient_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `CommunicationTarget_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CommunicationTarget_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `lastStatusUpdate` datetime DEFAULT NULL,
  `lastStatusUpdateMessage` varchar(255) DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` char(1) DEFAULT NULL,
  `communication_id` bigint(20) DEFAULT NULL,
  `recipient_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK9F259F38DF74E053` (`REV`),
  CONSTRAINT `FK9F259F38DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Communication_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Communication_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `creationTime` datetime DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) DEFAULT NULL,
  `originator_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKE9036287DF74E053` (`REV`),
  CONSTRAINT `FKE9036287DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DateFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DateFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultDate` bigint(20) NOT NULL,
  `format` varchar(255) DEFAULT NULL,
  `max_value` bigint(20) DEFAULT NULL,
  `minValue` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA44e08d38b0` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA44e08d38b0` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DateFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DateFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultDate` bigint(20) DEFAULT NULL,
  `format` varchar(255) DEFAULT NULL,
  `max_value` bigint(20) DEFAULT NULL,
  `minValue` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053f10d8e81` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053f10d8e81` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatAudio`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatAudio` (
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF77F8CE5C81E6316` (`id`),
  CONSTRAINT `FKF77F8CE5C81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatAudio_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatAudio_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKE1A0C03644EAD40A` (`id`,`REV`),
  CONSTRAINT `FKE1A0C03644EAD40A` FOREIGN KEY (`id`, `REV`) REFERENCES `EcatMediaFile_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatComment_EcatCommentItem_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatComment_EcatCommentItem_AUD` (
  `REV` int(11) NOT NULL,
  `com_id` bigint(20) NOT NULL,
  `item_id` bigint(20) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  PRIMARY KEY (`REV`,`com_id`,`item_id`),
  KEY `FK7C3E5721DF74E053` (`REV`),
  CONSTRAINT `FK7C3E5721DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatDocumentFile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatDocumentFile` (
  `documentType` varchar(255) DEFAULT NULL,
  `version` bigint(20) NOT NULL,
  `versioningName` varchar(255) DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  `thumbNail_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKE688C548C81E6316` (`id`),
  KEY `FKE688C5485C9543B2` (`thumbNail_id`),
  CONSTRAINT `FKE688C5485C9543B2` FOREIGN KEY (`thumbNail_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKE688C548C81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatDocumentFile_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatDocumentFile_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `documentType` varchar(255) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `versioningName` varchar(255) DEFAULT NULL,
  `thumbNail_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK3EC10F1944EAD40A` (`id`,`REV`),
  CONSTRAINT `FK3EC10F1944EAD40A` FOREIGN KEY (`id`, `REV`) REFERENCES `EcatMediaFile_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatImage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatImage` (
  `height` int(11) NOT NULL,
  `heightResized` int(11) NOT NULL,
  `width` int(11) NOT NULL,
  `widthResized` int(11) NOT NULL,
  `id` bigint(20) NOT NULL,
  `imageFileRezisedEditor_id` bigint(20) DEFAULT NULL,
  `imageThumbnailed_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF7EC9A6A20867C78` (`imageFileRezisedEditor_id`),
  KEY `FKF7EC9A6AC81E6316` (`id`),
  KEY `FKF7EC9A6A721017CE` (`imageThumbnailed_id`),
  CONSTRAINT `FKF7EC9A6A721017CE` FOREIGN KEY (`imageThumbnailed_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKF7EC9A6A20867C78` FOREIGN KEY (`imageFileRezisedEditor_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKF7EC9A6AC81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatImage_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatImage_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `height` int(11) DEFAULT NULL,
  `heightResized` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `widthResized` int(11) DEFAULT NULL,
  `imageFileRezisedEditor_id` bigint(20) DEFAULT NULL,
  `imageThumbnailed_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKA211833B44EAD40A` (`id`,`REV`),
  CONSTRAINT `FKA211833B44EAD40A` FOREIGN KEY (`id`, `REV`) REFERENCES `EcatMediaFile_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatMediaFile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatMediaFile` (
  `contentType` varchar(255) DEFAULT NULL,
  `extension` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileUri` varchar(255) DEFAULT NULL,
  `size` bigint(20) NOT NULL,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK55F3190FDF5C8EB5` (`id`),
  CONSTRAINT `FK55F3190FDF5C8EB5` FOREIGN KEY (`id`) REFERENCES `Record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatMediaFile_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatMediaFile_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `contentType` varchar(255) DEFAULT NULL,
  `extension` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileUri` varchar(255) DEFAULT NULL,
  `size` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK85BE2760F3DC1829` (`id`,`REV`),
  CONSTRAINT `FK85BE2760F3DC1829` FOREIGN KEY (`id`, `REV`) REFERENCES `Record_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatVideo`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatVideo` (
  `height` int(11) NOT NULL,
  `width` int(11) NOT NULL,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF8A2058AC81E6316` (`id`),
  CONSTRAINT `FKF8A2058AC81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatVideo_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatVideo_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `height` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK25DC5E5B44EAD40A` (`id`,`REV`),
  CONSTRAINT `FK25DC5E5B44EAD40A` FOREIGN KEY (`id`, `REV`) REFERENCES `EcatMediaFile_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Field`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Field` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `data` varchar(1000) DEFAULT NULL,
  `rtfData` longtext,
  `fieldForm_id` bigint(20) NOT NULL,
  `structuredDocument_id` bigint(20) DEFAULT NULL,
  `tempField_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK40BB0DA5A86BE23` (`structuredDocument_id`),
  KEY `FK40BB0DA4FB44926` (`tempField_id`),
  CONSTRAINT `FK40BB0DA4FB44926` FOREIGN KEY (`tempField_id`) REFERENCES `Field` (`id`),
  CONSTRAINT `FK40BB0DA5A86BE23` FOREIGN KEY (`structuredDocument_id`) REFERENCES `StructuredDocument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Field_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Field_AUD` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `fieldForm_id` bigint(20) DEFAULT NULL,
  `structuredDocument_id` bigint(20) DEFAULT NULL,
  `tempField_id` bigint(20) DEFAULT NULL,
  `rtfData` longtext,
  `data` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKC2DFE1ABDF74E053` (`REV`),
  CONSTRAINT `FKC2DFE1ABDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FilePropertys`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FilePropertys` (
  `fileUri` varchar(191) NOT NULL,
  `createDate` varchar(255) DEFAULT NULL,
  `fileCategory` varchar(255) DEFAULT NULL,
  `fileGroup` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileOwner` varchar(255) DEFAULT NULL,
  `fileSize` varchar(255) DEFAULT NULL,
  `fileState` varchar(255) DEFAULT NULL,
  `fileTag` varchar(255) DEFAULT NULL,
  `fileUser` varchar(255) DEFAULT NULL,
  `fileVersion` varchar(255) DEFAULT NULL,
  `linkId` varchar(255) DEFAULT NULL,
  `linkInfo` varchar(255) DEFAULT NULL,
  `updateDate` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`fileUri`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FilePropertys_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FilePropertys_AUD` (
  `fileUri` varchar(191) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `createDate` varchar(255) DEFAULT NULL,
  `fileCategory` varchar(255) DEFAULT NULL,
  `fileGroup` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileOwner` varchar(255) DEFAULT NULL,
  `fileSize` varchar(255) DEFAULT NULL,
  `fileState` varchar(255) DEFAULT NULL,
  `fileTag` varchar(255) DEFAULT NULL,
  `fileUser` varchar(255) DEFAULT NULL,
  `fileVersion` varchar(255) DEFAULT NULL,
  `linkId` varchar(255) DEFAULT NULL,
  `linkInfo` varchar(255) DEFAULT NULL,
  `updateDate` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`fileUri`,`REV`),
  KEY `FK8C447FD3DF74E053` (`REV`),
  CONSTRAINT `FK8C447FD3DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Folder` (
  `systemFolder` bit(1) NOT NULL,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK7DC2088E94BB9466` (`id`),
  CONSTRAINT `FK7DC2088E94BB9466` FOREIGN KEY (`id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Folder_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Folder_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `systemFolder` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK457B3F5F31EEDD5A` (`id`,`REV`),
  CONSTRAINT `FK457B3F5F31EEDD5A` FOREIGN KEY (`id`, `REV`) REFERENCES `BaseRecord_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FormUsage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FormUsage` (
  `id` bigint(20) NOT NULL,
  `formStableID` varchar(255) DEFAULT NULL,
  `lastUsedTimeInMillis` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKE2C2FB1DDE6F978E` (`user_id`),
  CONSTRAINT `FKE2C2FB1DDE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `GroupMessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GroupMessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) NOT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) NOT NULL,
  `messageType` int(11) NOT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF1E9FFB664CCD43Dc3272305b5828b64` (`originator_id`),
  KEY `FKB5828B641CC96626` (`group_id`),
  KEY `FKF1E9FFB6CFF3DF14c3272305b5828b64` (`record_id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305b5828b64` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FKB5828B641CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`),
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305b5828b64` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `GroupMessageOrRequest_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GroupMessageOrRequest_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `creationTime` datetime DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) DEFAULT NULL,
  `originator_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) DEFAULT NULL,
  `messageType` int(11) DEFAULT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKE9036287DF74E053c5504656d0f26735` (`REV`),
  CONSTRAINT `FKE9036287DF74E053c5504656d0f26735` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `IconEntity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IconEntity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `height` int(11) NOT NULL,
  `iconImage` longblob,
  `imgName` varchar(255) DEFAULT NULL,
  `imgType` varchar(255) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `width` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ImageBlob`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ImageBlob` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data` longblob,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ImageBlob_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ImageBlob_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `data` longblob,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK595DF949DF74E053` (`REV`),
  CONSTRAINT `FK595DF949DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `MessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) NOT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) NOT NULL,
  `messageType` int(11) NOT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF1E9FFB664CCD43Dc3272305` (`originator_id`),
  KEY `FKF1E9FFB6CFF3DF14c3272305` (`record_id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `MessageOrRequest_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MessageOrRequest_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `creationTime` datetime DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) DEFAULT NULL,
  `originator_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) DEFAULT NULL,
  `messageType` int(11) DEFAULT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKE9036287DF74E053c5504656` (`REV`),
  CONSTRAINT `FKE9036287DF74E053c5504656` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Notebook`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Notebook` (
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK621F05FBCB733ED2` (`id`),
  CONSTRAINT `FK621F05FBCB733ED2` FOREIGN KEY (`id`) REFERENCES `Folder` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Notebook_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Notebook_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK8F11BE4C96B371C6` (`id`,`REV`),
  CONSTRAINT `FK8F11BE4C96B371C6` FOREIGN KEY (`id`, `REV`) REFERENCES `Folder_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Notification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Notification` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) NOT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `notificationMessage` varchar(255) DEFAULT NULL,
  `notificationType` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF1E9FFB664CCD43D2d45dd0b` (`originator_id`),
  KEY `FKF1E9FFB6CFF3DF142d45dd0b` (`record_id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF142d45dd0b` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FKF1E9FFB664CCD43D2d45dd0b` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Notification_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Notification_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `creationTime` datetime DEFAULT NULL,
  `message` varchar(255) DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `typeCode` int(11) DEFAULT NULL,
  `originator_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `notificationMessage` varchar(255) DEFAULT NULL,
  `notificationType` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKE9036287DF74E0532dd68d5c` (`REV`),
  CONSTRAINT `FKE9036287DF74E0532dd68d5c` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `NumberFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `NumberFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `decimalPlaces` tinyint(4) DEFAULT NULL,
  `defaultNumberValue` double DEFAULT NULL,
  `maxNumberValue` double DEFAULT NULL,
  `minNumberValue` double DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA4464c6b375` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA4464c6b375` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `NumberFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `NumberFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `decimalPlaces` tinyint(4) DEFAULT NULL,
  `defaultNumberValue` double DEFAULT NULL,
  `maxNumberValue` double DEFAULT NULL,
  `minNumberValue` double DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E05372d21ec6` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E05372d21ec6` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `OriginalFormat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `OriginalFormat` (
  `data` longblob,
  `extension` varchar(255) DEFAULT NULL,
  `indexContent` longtext,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC69D948DF5C8EB5` (`id`),
  CONSTRAINT `FKC69D948DF5C8EB5` FOREIGN KEY (`id`) REFERENCES `Record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `REVINFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REVINFO` (
  `REV` int(11) NOT NULL AUTO_INCREMENT,
  `REVTSTMP` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`REV`)
) ENGINE=InnoDB AUTO_INCREMENT=9 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSChemElement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSChemElement` (
  `id` bigint(20) NOT NULL,
  `chemElements` longtext,
  `dataImage` longblob,
  `parentId` bigint(20) NOT NULL,
  `rgroupId` int(11),
  `smilesString` longtext,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSChemElement_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSChemElement_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `chemElements` longtext,
  `dataImage` longblob,
  `parentId` bigint(20) DEFAULT NULL,
  `rgroupId` int(11),
  `smilesString` longtext,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK2B28D43FDF74E053` (`REV`),
  CONSTRAINT `FK2B28D43FDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSForm` (
  `id` bigint(20) NOT NULL,
  `groupPermissionType` varchar(255) DEFAULT NULL,
  `ownerPermissionType` varchar(255) DEFAULT NULL,
  `worldPermissionType` varchar(255) DEFAULT NULL,
  `current` bit(1) NOT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime NOT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) NOT NULL,
  `name` varchar(255) NOT NULL,
  `exemplified` bit(1) NOT NULL,
  `formType` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) NOT NULL,
  `publishingState` varchar(255) DEFAULT NULL,
  `stableID` varchar(255) DEFAULT NULL,
  `tmpTag` varchar(255) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `exemplar_id` bigint(20) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `previousVersion_id` bigint(20) DEFAULT NULL,
  `tempForm_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `stableid` (`stableID`),
  KEY `FK90A082A5813663E7` (`previousVersion_id`),
  KEY `FK90A082A5E5EA33C7` (`exemplar_id`),
  KEY `FK90A082A54A5647A6` (`owner_id`),
  KEY `FK90A082A522322FF0` (`tempForm_id`),
  CONSTRAINT `FK90A082A522322FF0` FOREIGN KEY (`tempForm_id`) REFERENCES `RSForm` (`id`),
  CONSTRAINT `FK90A082A54A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK90A082A5813663E7` FOREIGN KEY (`previousVersion_id`) REFERENCES `RSForm` (`id`),
  CONSTRAINT `FK90A082A5E5EA33C7` FOREIGN KEY (`exemplar_id`) REFERENCES `StructuredDocument` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `groupPermissionType` varchar(255) DEFAULT NULL,
  `ownerPermissionType` varchar(255) DEFAULT NULL,
  `worldPermissionType` varchar(255) DEFAULT NULL,
  `current` bit(1) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `exemplified` bit(1) DEFAULT NULL,
  `formType` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `publishingState` varchar(255) DEFAULT NULL,
  `stableID` varchar(255) DEFAULT NULL,
  `tmpTag` varchar(255) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `exemplar_id` bigint(20) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `previousVersion_id` bigint(20) DEFAULT NULL,
  `tempForm_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK71D0D5F6DF74E053` (`REV`),
  CONSTRAINT `FK71D0D5F6DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSMetaData`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSMetaData` (
  `id` bigint(20) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `initialized` bit(1) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RadioFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RadioFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultRadioOption` varchar(255) DEFAULT NULL,
  `radioOption` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA447a8f3743` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA447a8f3743` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RadioFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RadioFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultRadioOption` varchar(255) DEFAULT NULL,
  `radioOption` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053a5f80b94` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053a5f80b94` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Record` (
  `id` bigint(20) NOT NULL,
  `tempRecord_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK91AB587194BB9466` (`id`),
  KEY `FK91AB58717C8BA40F` (`tempRecord_id`),
  CONSTRAINT `FK91AB58717C8BA40F` FOREIGN KEY (`tempRecord_id`) REFERENCES `Record` (`id`),
  CONSTRAINT `FK91AB587194BB9466` FOREIGN KEY (`id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordGroupSharing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordGroupSharing` (
  `id` bigint(20) NOT NULL,
  `shared_id` bigint(20) DEFAULT NULL,
  `sharee_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2AA5A06E3676B680` (`shared_id`),
  CONSTRAINT `FK2AA5A06E3676B680` FOREIGN KEY (`shared_id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordToFolder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordToFolder` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `recordInFolderDeleted` bit(1) DEFAULT NULL,
  `userName` varchar(255) DEFAULT NULL,
  `folder_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3280CA5AEAE40843` (`folder_id`),
  KEY `FK3280CA5ACFF3DF14` (`record_id`),
  CONSTRAINT `FK3280CA5ACFF3DF14` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FK3280CA5AEAE40843` FOREIGN KEY (`folder_id`) REFERENCES `Folder` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=110 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordToFolder_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordToFolder_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `recordInFolderDeleted` bit(1) DEFAULT NULL,
  `userName` varchar(255) DEFAULT NULL,
  `folder_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK162C3B2BDF74E053` (`REV`),
  CONSTRAINT `FK162C3B2BDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Record_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Record_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `tempRecord_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKA2A3E5C231EEDD5A` (`id`,`REV`),
  CONSTRAINT `FKA2A3E5C231EEDD5A` FOREIGN KEY (`id`, `REV`) REFERENCES `BaseRecord_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Role_permissionStrings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Role_permissionStrings` (
  `Role_id` bigint(20) NOT NULL,
  `permissionStrings` varchar(255) DEFAULT NULL,
  KEY `FK10AF424A3944D3AE` (`Role_id`),
  CONSTRAINT `FK10AF424A3944D3AE` FOREIGN KEY (`Role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Signature`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Signature` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `signatureDate` datetime DEFAULT NULL,
  `statement` varchar(255) DEFAULT NULL,
  `recordSigned_id` bigint(20) NOT NULL,
  `signer_id` bigint(20) NOT NULL,
  `witnessRequest_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `recordSigned_id` (`recordSigned_id`),
  KEY `recordIndex` (`recordSigned_id`),
  KEY `FKB76FB898276963CF` (`signer_id`),
  KEY `FKB76FB8988CFABD87` (`recordSigned_id`),
  CONSTRAINT `FKB76FB8988CFABD87` FOREIGN KEY (`recordSigned_id`) REFERENCES `Record` (`id`),
  CONSTRAINT `FKB76FB898276963CF` FOREIGN KEY (`signer_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `StringFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `StringFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultStringValue` varchar(255) DEFAULT NULL,
  `ifPassword` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA44df7f0fad` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA44df7f0fad` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `StringFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `StringFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultStringValue` varchar(255) DEFAULT NULL,
  `ifPassword` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053e5119efe` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053e5119efe` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `StructuredDocument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `StructuredDocument` (
  `deltaString` varchar(255) DEFAULT NULL,
  `previousIdString` varchar(255) DEFAULT NULL,
  `docTag` varchar(255) DEFAULT NULL,
  `temporaryDoc` bit(1) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8D9991ACDF5C8EB5` (`id`),
  KEY `FK8D9991ACB5F0AA44` (`form_id`),
  CONSTRAINT `FK8D9991ACB5F0AA44` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`),
  CONSTRAINT `FK8D9991ACDF5C8EB5` FOREIGN KEY (`id`) REFERENCES `Record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `StructuredDocument_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `StructuredDocument_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `deltaString` varchar(255) DEFAULT NULL,
  `previousIdString` varchar(255) DEFAULT NULL,
  `docTag` varchar(255) DEFAULT NULL,
  `temporaryDoc` bit(1) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK1E0D097DF3DC1829` (`id`,`REV`),
  CONSTRAINT `FK1E0D097DF3DC1829` FOREIGN KEY (`id`, `REV`) REFERENCES `Record_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SystemFolder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SystemFolder` (
  `filePath` varchar(255) DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKCA512F9DDF5C8EB5` (`id`),
  CONSTRAINT `FKCA512F9DDF5C8EB5` FOREIGN KEY (`id`) REFERENCES `Record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SystemFolder_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SystemFolder_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `filePath` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKD8F246EEF3DC1829` (`id`,`REV`),
  CONSTRAINT `FKD8F246EEF3DC1829` FOREIGN KEY (`id`, `REV`) REFERENCES `Record_AUD` (`id`, `REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `TextFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TextFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultValue` longtext,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA448b64a211` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA448b64a211` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `TextFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TextFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultValue` longtext,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053255f5f62` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053255f5f62` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `TimeFieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TimeFieldForm` (
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultTime` bigint(20) NOT NULL,
  `maxTime` bigint(20) NOT NULL,
  `minTime` bigint(20) NOT NULL,
  `timeFormat` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC2D534BEB5F0AA44f409e871` (`form_id`),
  CONSTRAINT `FKC2D534BEB5F0AA44f409e871` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `TimeFieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TimeFieldForm_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `helpText` varchar(255) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultTime` bigint(20) DEFAULT NULL,
  `maxTime` bigint(20) DEFAULT NULL,
  `minTime` bigint(20) DEFAULT NULL,
  `timeFormat` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK37BCD38FDF74E053933a75c2` (`REV`),
  CONSTRAINT `FK37BCD38FDF74E053933a75c2` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `User`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `User` (
  `id` bigint(20) NOT NULL,
  `version` int(11) DEFAULT NULL,
  `account_expired` bit(1) NOT NULL,
  `account_locked` bit(1) NOT NULL,
  `contentInitialized` bit(1) NOT NULL,
  `credentials_expired` bit(1) NOT NULL,
  `email` varchar(255) NOT NULL,
  `account_enabled` bit(1) DEFAULT NULL,
  `first_name` varchar(50) NOT NULL,
  `lastLogin` datetime DEFAULT NULL,
  `last_name` varchar(50) NOT NULL,
  `loginFailure` datetime DEFAULT NULL,
  `numConsecutiveLoginFailures` tinyint(4) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `password_hint` varchar(255) DEFAULT NULL,
  `salt` varchar(24) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `rootFolder_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  KEY `FK285FEBB7B465A1` (`rootFolder_id`),
  CONSTRAINT `FK285FEBB7B465A1` FOREIGN KEY (`rootFolder_id`) REFERENCES `Folder` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserGroup` (
  `id` bigint(20) NOT NULL,
  `includePermissions` bit(1) NOT NULL,
  `primaryGroup` bit(1) NOT NULL,
  `roleInGroup` int(11) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8A5BE154DE6F978E` (`user_id`),
  KEY `FK8A5BE1541CC96626` (`group_id`),
  CONSTRAINT `FK8A5BE1541CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`),
  CONSTRAINT `FK8A5BE154DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserGroup_permissionStrings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserGroup_permissionStrings` (
  `UserGroup_id` bigint(20) NOT NULL,
  `permissionStrings` varchar(255) DEFAULT NULL,
  KEY `FKAC75BB88A5B0DBE6` (`UserGroup_id`),
  CONSTRAINT `FKAC75BB88A5B0DBE6` FOREIGN KEY (`UserGroup_id`) REFERENCES `UserGroup` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserPreference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserPreference` (
  `id` bigint(20) NOT NULL,
  `preference` int(11) DEFAULT NULL,
  `value` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9A3C4F26DE6F978E` (`user_id`),
  CONSTRAINT `FK9A3C4F26DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Witness`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Witness` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `optionString` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) NOT NULL,
  `witnessesDate` datetime DEFAULT NULL,
  `signature_id` bigint(20) DEFAULT NULL,
  `witness_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKB4012D198E86C986` (`signature_id`),
  KEY `FKB4012D19C5434F60` (`witness_id`),
  CONSTRAINT `FKB4012D19C5434F60` FOREIGN KEY (`witness_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKB4012D198E86C986` FOREIGN KEY (`signature_id`) REFERENCES `Signature` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecatImageAnnotation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecatImageAnnotation` (
  `id` bigint(20) NOT NULL,
  `annotations` longtext,
  `data` longblob,
  `imageId` bigint(20) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `textAnnotations` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecatImageAnnotation_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecatImageAnnotation_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `annotations` longtext,
  `data` longblob,
  `imageId` bigint(20) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `textAnnotations` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK3E4FF6AADF74E053` (`REV`),
  CONSTRAINT `FK3E4FF6AADF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecat_comm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecat_comm` (
  `com_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `com_author` varchar(255) DEFAULT NULL,
  `com_desc` varchar(255) DEFAULT NULL,
  `com_name` varchar(255) DEFAULT NULL,
  `create_date` datetime DEFAULT NULL,
  `updater_id` varchar(255) DEFAULT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  `sequence` int(11) DEFAULT NULL,
  `update_date` datetime DEFAULT NULL,
  PRIMARY KEY (`com_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecat_comm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecat_comm_AUD` (
  `com_id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `com_author` varchar(255) DEFAULT NULL,
  `com_desc` varchar(255) DEFAULT NULL,
  `com_name` varchar(255) DEFAULT NULL,
  `create_date` datetime DEFAULT NULL,
  `updater_id` varchar(255) DEFAULT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  `sequence` int(11) DEFAULT NULL,
  `update_date` datetime DEFAULT NULL,
  PRIMARY KEY (`com_id`,`REV`),
  KEY `FK10CD2A0BDF74E053` (`REV`),
  CONSTRAINT `FK10CD2A0BDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecat_comm_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecat_comm_item` (
  `item_id` bigint(20) NOT NULL AUTO_INCREMENT,
  `com_id` bigint(20) DEFAULT NULL,
  `create_date` datetime DEFAULT NULL,
  `gmt_offset` int(11) DEFAULT NULL,
  `item_content` varchar(1000) DEFAULT NULL,
  `item_name` varchar(255) DEFAULT NULL,
  `updater_id` varchar(255) DEFAULT NULL,
  `update_date` datetime DEFAULT NULL,
  PRIMARY KEY (`item_id`),
  KEY `FK8EABEF8BC8C5433` (`com_id`),
  CONSTRAINT `FK8EABEF8BC8C5433` FOREIGN KEY (`com_id`) REFERENCES `ecat_comm` (`com_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecat_comm_item_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecat_comm_item_AUD` (
  `item_id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `com_id` bigint(20) DEFAULT NULL,
  `create_date` datetime DEFAULT NULL,
  `gmt_offset` int(11) DEFAULT NULL,
  `item_content` varchar(1000) DEFAULT NULL,
  `item_name` varchar(255) DEFAULT NULL,
  `updater_id` varchar(255) DEFAULT NULL,
  `update_date` datetime DEFAULT NULL,
  PRIMARY KEY (`item_id`,`REV`),
  KEY `FK72EB0C9DF74E053` (`REV`),
  CONSTRAINT `FK72EB0C9DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `hibernate_sequences`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `hibernate_sequences` (
  `sequence_name` varchar(255) DEFAULT NULL,
  `sequence_next_hi_value` int(11) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `roles`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `roles` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `description` varchar(255) NOT NULL,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_roles_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `rsGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `rsGroup` (
  `id` bigint(20) NOT NULL,
  `version` int(11) DEFAULT NULL,
  `communalGroupFolderId` bigint(20) DEFAULT NULL,
  `displayName` varchar(255) DEFAULT NULL,
  `groupFolderCreated` bit(1) NOT NULL,
  `groupFolderWanted` bit(1) NOT NULL,
  `groupType` int(11) DEFAULT NULL,
  `uniqueName` varchar(191) NOT NULL,
  `parent_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniqueName` (`uniqueName`),
  KEY `FK56E1B8BE7A01A01B` (`parent_id`),
  CONSTRAINT `FK56E1B8BE7A01A01B` FOREIGN KEY (`parent_id`) REFERENCES `rsGroup` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `user_role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `user_role` (
  `user_id` bigint(20) NOT NULL,
  `role_id` bigint(20) NOT NULL,
  PRIMARY KEY (`user_id`,`role_id`),
  KEY `FK143BF46A3944D3AE` (`role_id`),
  KEY `FK143BF46ADE6F978E` (`user_id`),
  CONSTRAINT `FK143BF46ADE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK143BF46A3944D3AE` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;


