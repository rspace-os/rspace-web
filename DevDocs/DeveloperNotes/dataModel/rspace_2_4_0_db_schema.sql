
/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
DROP TABLE IF EXISTS `App`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `App` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `defaultEnabled` bit(1) NOT NULL,
  `label` varchar(255) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `AppConfigElement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AppConfigElement` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `value` varchar(255) DEFAULT NULL,
  `appConfigElementDescriptor_id` bigint(20) DEFAULT NULL,
  `appConfigElementSet_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK14AF9CD95B6A1813` (`appConfigElementSet_id`),
  KEY `FK14AF9CD9A1FF1FA1` (`appConfigElementDescriptor_id`),
  CONSTRAINT `FK14AF9CD95B6A1813` FOREIGN KEY (`appConfigElementSet_id`) REFERENCES `AppConfigElementSet` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK14AF9CD9A1FF1FA1` FOREIGN KEY (`appConfigElementDescriptor_id`) REFERENCES `AppConfigElementDescriptor` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `AppConfigElementDescriptor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AppConfigElementDescriptor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `descriptor_id` bigint(20) DEFAULT NULL,
  `app_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK22EED1284B47DE79` (`descriptor_id`),
  KEY `FK22EED128C68E22F3` (`app_id`),
  CONSTRAINT `FK22EED1284B47DE79` FOREIGN KEY (`descriptor_id`) REFERENCES `PropertyDescriptor` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK22EED128C68E22F3` FOREIGN KEY (`app_id`) REFERENCES `App` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=30 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `AppConfigElementSet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `AppConfigElementSet` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `userAppConfig_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK403ECCC9CD8C853` (`userAppConfig_id`),
  CONSTRAINT `FK403ECCC9CD8C853` FOREIGN KEY (`userAppConfig_id`) REFERENCES `UserAppConfig` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ArchivalCheckSum`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ArchivalCheckSum` (
  `uid` varchar(191) NOT NULL,
  `algorithm` varchar(255) DEFAULT NULL,
  `archivalDate` bigint(20) NOT NULL,
  `checkSum` bigint(20) NOT NULL,
  `zipName` varchar(255) DEFAULT NULL,
  `zipSize` bigint(20) NOT NULL,
  `downloadTimeExpired` bit(1) NOT NULL,
  `exporter_id` bigint(20) DEFAULT NULL,
  `zipContentCheckSum` varchar(256) DEFAULT NULL,
  PRIMARY KEY (`uid`),
  KEY `FKCD2D75D16AAC5038` (`exporter_id`),
  CONSTRAINT `FKCD2D75D16AAC5038` FOREIGN KEY (`exporter_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ArchiveVersionToAppVersion`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ArchiveVersionToAppVersion` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `toMajor` int(11) NOT NULL,
  `toMinor` int(11) DEFAULT NULL,
  `toQualifier` int(11) DEFAULT NULL,
  `toSuffix` varchar(255) DEFAULT NULL,
  `schemaName` varchar(255) DEFAULT NULL,
  `schemaVersion` bigint(20) NOT NULL,
  `fromMajor` int(11) DEFAULT NULL,
  `fromMinor` int(11) DEFAULT NULL,
  `fromQualifier` int(11) DEFAULT NULL,
  `fromSuffix` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=7 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_EXECUTION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_EXECUTION` (
  `JOB_EXECUTION_ID` bigint(20) NOT NULL,
  `VERSION` bigint(20) DEFAULT NULL,
  `JOB_INSTANCE_ID` bigint(20) NOT NULL,
  `CREATE_TIME` datetime NOT NULL,
  `START_TIME` datetime DEFAULT NULL,
  `END_TIME` datetime DEFAULT NULL,
  `STATUS` varchar(10) DEFAULT NULL,
  `EXIT_CODE` varchar(2500) DEFAULT NULL,
  `EXIT_MESSAGE` varchar(2500) DEFAULT NULL,
  `LAST_UPDATED` datetime DEFAULT NULL,
  `JOB_CONFIGURATION_LOCATION` varchar(2500) DEFAULT NULL,
  PRIMARY KEY (`JOB_EXECUTION_ID`),
  KEY `JOB_INST_EXEC_FK` (`JOB_INSTANCE_ID`),
  CONSTRAINT `JOB_INST_EXEC_FK` FOREIGN KEY (`JOB_INSTANCE_ID`) REFERENCES `BATCH_JOB_INSTANCE` (`JOB_INSTANCE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_EXECUTION_CONTEXT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_EXECUTION_CONTEXT` (
  `JOB_EXECUTION_ID` bigint(20) NOT NULL,
  `SHORT_CONTEXT` varchar(2500) NOT NULL,
  `SERIALIZED_CONTEXT` text DEFAULT NULL,
  PRIMARY KEY (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_CTX_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_EXECUTION_PARAMS`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_EXECUTION_PARAMS` (
  `JOB_EXECUTION_ID` bigint(20) NOT NULL,
  `TYPE_CD` varchar(6) NOT NULL,
  `KEY_NAME` varchar(100) NOT NULL,
  `STRING_VAL` varchar(250) DEFAULT NULL,
  `DATE_VAL` datetime DEFAULT NULL,
  `LONG_VAL` bigint(20) DEFAULT NULL,
  `DOUBLE_VAL` double DEFAULT NULL,
  `IDENTIFYING` char(1) NOT NULL,
  KEY `JOB_EXEC_PARAMS_FK` (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_PARAMS_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_EXECUTION_SEQ`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_EXECUTION_SEQ` (
  `ID` bigint(20) NOT NULL,
  `UNIQUE_KEY` char(1) NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_INSTANCE`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_INSTANCE` (
  `JOB_INSTANCE_ID` bigint(20) NOT NULL,
  `VERSION` bigint(20) DEFAULT NULL,
  `JOB_NAME` varchar(100) NOT NULL,
  `JOB_KEY` varchar(32) NOT NULL,
  PRIMARY KEY (`JOB_INSTANCE_ID`),
  UNIQUE KEY `JOB_INST_UN` (`JOB_NAME`,`JOB_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_JOB_SEQ`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_JOB_SEQ` (
  `ID` bigint(20) NOT NULL,
  `UNIQUE_KEY` char(1) NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_STEP_EXECUTION`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_STEP_EXECUTION` (
  `STEP_EXECUTION_ID` bigint(20) NOT NULL,
  `VERSION` bigint(20) NOT NULL,
  `STEP_NAME` varchar(100) NOT NULL,
  `JOB_EXECUTION_ID` bigint(20) NOT NULL,
  `START_TIME` datetime NOT NULL,
  `END_TIME` datetime DEFAULT NULL,
  `STATUS` varchar(10) DEFAULT NULL,
  `COMMIT_COUNT` bigint(20) DEFAULT NULL,
  `READ_COUNT` bigint(20) DEFAULT NULL,
  `FILTER_COUNT` bigint(20) DEFAULT NULL,
  `WRITE_COUNT` bigint(20) DEFAULT NULL,
  `READ_SKIP_COUNT` bigint(20) DEFAULT NULL,
  `WRITE_SKIP_COUNT` bigint(20) DEFAULT NULL,
  `PROCESS_SKIP_COUNT` bigint(20) DEFAULT NULL,
  `ROLLBACK_COUNT` bigint(20) DEFAULT NULL,
  `EXIT_CODE` varchar(2500) DEFAULT NULL,
  `EXIT_MESSAGE` varchar(2500) DEFAULT NULL,
  `LAST_UPDATED` datetime DEFAULT NULL,
  PRIMARY KEY (`STEP_EXECUTION_ID`),
  KEY `JOB_EXEC_STEP_FK` (`JOB_EXECUTION_ID`),
  CONSTRAINT `JOB_EXEC_STEP_FK` FOREIGN KEY (`JOB_EXECUTION_ID`) REFERENCES `BATCH_JOB_EXECUTION` (`JOB_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_STEP_EXECUTION_CONTEXT`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_STEP_EXECUTION_CONTEXT` (
  `STEP_EXECUTION_ID` bigint(20) NOT NULL,
  `SHORT_CONTEXT` varchar(2500) NOT NULL,
  `SERIALIZED_CONTEXT` text DEFAULT NULL,
  PRIMARY KEY (`STEP_EXECUTION_ID`),
  CONSTRAINT `STEP_EXEC_CTX_FK` FOREIGN KEY (`STEP_EXECUTION_ID`) REFERENCES `BATCH_STEP_EXECUTION` (`STEP_EXECUTION_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BATCH_STEP_EXECUTION_SEQ`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BATCH_STEP_EXECUTION_SEQ` (
  `ID` bigint(20) NOT NULL,
  `UNIQUE_KEY` char(1) NOT NULL,
  UNIQUE KEY `UNIQUE_KEY_UN` (`UNIQUE_KEY`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BR_AUD_TEMP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BR_AUD_TEMP` (
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
  `acl` varchar(2500) DEFAULT NULL,
  `signed` bit(1) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Barcode`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Barcode` (
  `id` bigint(20) NOT NULL,
  `barcodeData` varchar(255) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime NOT NULL,
  `deleted` bit(1) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `format` varchar(255) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_nmicitoljb0fsgx7540tnvekn` (`container_id`),
  KEY `FK_rhmn3wu9elicxkb0nvpbjtgkg` (`subSample_id`),
  KEY `FK_sdjfs8yqacwgh08c4cd5ldb4n` (`sample_id`),
  CONSTRAINT `FK_nmicitoljb0fsgx7540tnvekn` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_rhmn3wu9elicxkb0nvpbjtgkg` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_sdjfs8yqacwgh08c4cd5ldb4n` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Barcode_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Barcode_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `barcodeData` varchar(255) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `format` varchar(255) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_90by6wcwxmkbkel3hs3lfagjv` (`REV`),
  CONSTRAINT `FK_90by6wcwxmkbkel3hs3lfagjv` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `acl` varchar(2500) DEFAULT NULL,
  `signed` bit(1) NOT NULL,
  `type` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `fromImport` bit(1) NOT NULL DEFAULT b'0',
  `originalCreatorUsername` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `isDeleted` (`deleted`),
  KEY `FK43851AA24A5647A6` (`owner_id`),
  KEY `record_name_idx` (`name`),
  CONSTRAINT `FK43851AA24A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=216 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
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
  `acl` varchar(2500) DEFAULT NULL,
  `signed` bit(1) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `witnessed` bit(1) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `fromImport` bit(1) NOT NULL DEFAULT b'0',
  `originalCreatorUsername` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK707F2773DF74E053` (`REV`),
  KEY `FK_BaseRecord_AUD_owner_id` (`owner_id`),
  CONSTRAINT `FK707F2773DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`),
  CONSTRAINT `FK_BaseRecord_AUD_owner_id` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Basket`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Basket` (
  `id` bigint(20) NOT NULL,
  `itemCount` int(11) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `owner_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_52lo172gwmhxifhonhr4krl5r` (`owner_id`),
  CONSTRAINT `FK_52lo172gwmhxifhonhr4krl5r` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `BasketItem`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `BasketItem` (
  `id` bigint(20) NOT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `basket_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_19qj828ivrph7voqg9am03irs` (`container_id`),
  KEY `FK_bv5a3yvmfygrtjrp4jxnqikvv` (`basket_id`),
  KEY `FK_ipf7ysvodetjl5nvlp2qev2qr` (`subSample_id`),
  KEY `FK_ptl7gofdm45nq7c3eso6e7fwa` (`sample_id`),
  CONSTRAINT `FK_19qj828ivrph7voqg9am03irs` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_bv5a3yvmfygrtjrp4jxnqikvv` FOREIGN KEY (`basket_id`) REFERENCES `Basket` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_ipf7ysvodetjl5nvlp2qev2qr` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_ptl7gofdm45nq7c3eso6e7fwa` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ClustermarketBookings`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ClustermarketBookings` (
  `id` bigint(20) NOT NULL,
  `data` longtext NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ClustermarketEquipment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ClustermarketEquipment` (
  `id` bigint(20) NOT NULL,
  `data` longtext NOT NULL,
  PRIMARY KEY (`id`)
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
  `lastStatusUpdateMessage` longtext DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `type` char(1) DEFAULT NULL,
  `communication_id` bigint(20) NOT NULL,
  `recipient_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FKA4C03CE73292C680` (`recipient_id`),
  CONSTRAINT `FKA4C03CE73292C680` FOREIGN KEY (`recipient_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Community`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Community` (
  `id` bigint(20) NOT NULL,
  `creationDate` datetime DEFAULT NULL,
  `displayName` varchar(255) DEFAULT NULL,
  `uniqueName` varchar(100) DEFAULT NULL,
  `profileText` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniquename` (`uniqueName`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Container`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Container` (
  `id` bigint(20) NOT NULL,
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
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `gridLayoutColumnsNumber` int(11) NOT NULL,
  `gridLayoutRowsNumber` int(11) NOT NULL,
  `canStoreContainers` bit(1) NOT NULL,
  `canStoreSamples` bit(1) NOT NULL,
  `contentCount` int(11) NOT NULL,
  `containerType` varchar(255) NOT NULL,
  `owner_id` bigint(20) NOT NULL,
  `parentLocation_id` bigint(20) DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `locationsImageFileProperty_id` bigint(20) DEFAULT NULL,
  `locationsCount` int(11) NOT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `gridLayoutColumnsLabelType` varchar(20) NOT NULL,
  `gridLayoutRowsLabelType` varchar(20) NOT NULL,
  `contentCountContainers` int(11) NOT NULL,
  `contentCountSubSamples` int(11) NOT NULL,
  `version` bigint(20) NOT NULL,
  `lastNonWorkbenchParent_id` bigint(20) DEFAULT NULL,
  `lastMoveDate` datetime(6) DEFAULT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_fvov1sytvpbhio02yjgdyp6a` (`owner_id`),
  KEY `FK_e1gbt89odqh5aj0x65vj8c8en` (`imageFileProperty_id`),
  KEY `FK_kc5u6kx28vg4lqxrpk1buksrt` (`thumbnailFileProperty_id`),
  KEY `FK_abcu6kx28vg4lqxrpk1bukabc` (`locationsImageFileProperty_id`),
  KEY `FK_sok9ajva0xlfa4g3mfsnbrf2d` (`parentLocation_id`),
  KEY `FK_6rcxehrv1uqvfrpghibckb9yb` (`lastNonWorkbenchParent_id`),
  CONSTRAINT `FK_6rcxehrv1uqvfrpghibckb9yb` FOREIGN KEY (`lastNonWorkbenchParent_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_abcu6kx28vg4lqxrpk1bukabc` FOREIGN KEY (`locationsImageFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_e1gbt89odqh5aj0x65vj8c8en` FOREIGN KEY (`imageFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_fvov1sytvpbhio02yjgdyp6a` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_kc5u6kx28vg4lqxrpk1buksrt` FOREIGN KEY (`thumbnailFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_sok9ajva0xlfa4g3mfsnbrf2d` FOREIGN KEY (`parentLocation_id`) REFERENCES `ContainerLocation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ContainerLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ContainerLocation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `coordX` int(11) NOT NULL,
  `coordY` int(11) NOT NULL,
  `container_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_o6nkksjhnmo72c9bn9cdu2hjg` (`container_id`),
  CONSTRAINT `FK_o6nkksjhnmo72c9bn9cdu2hjg` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Container_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Container_AUD` (
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
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `gridLayoutRowsNumber` int(11) DEFAULT NULL,
  `gridLayoutColumnsNumber` int(11) DEFAULT NULL,
  `canStoreContainers` bit(1) DEFAULT NULL,
  `canStoreSamples` bit(1) DEFAULT NULL,
  `contentCount` int(11) DEFAULT NULL,
  `containerType` varchar(255) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `parentLocation_id` bigint(20) DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `locationsImageFileProperty_id` bigint(20) DEFAULT NULL,
  `locationsCount` int(11) DEFAULT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `gridLayoutColumnsLabelType` varchar(20) DEFAULT NULL,
  `gridLayoutRowsLabelType` varchar(20) DEFAULT NULL,
  `contentCountContainers` int(11) DEFAULT NULL,
  `contentCountSubSamples` int(11) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `lastNonWorkbenchParent_id` bigint(20) DEFAULT NULL,
  `lastMoveDate` datetime(6) DEFAULT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_iptwjnw7lw2p35v50etkxwoib` (`REV`),
  CONSTRAINT `FK_iptwjnw7lw2p35v50etkxwoib` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `CreateGroupMessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CreateGroupMessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` longtext DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) NOT NULL,
  `messageType` int(11) NOT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  `groupName` varchar(255) DEFAULT NULL,
  `creator_id` bigint(20) DEFAULT NULL,
  `target_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKDD6287C8396B9B8D` (`creator_id`),
  KEY `FKDD6287C8B697F408` (`target_id`),
  KEY `FKF1E9FFB664CCD43Dc3272305dd6287c8` (`originator_id`),
  KEY `FKF1E9FFB6CFF3DF14c3272305dd6287c8` (`record_id`),
  CONSTRAINT `FKDD6287C8396B9B8D` FOREIGN KEY (`creator_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKDD6287C8B697F408` FOREIGN KEY (`target_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305dd6287c8` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305dd6287c8` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `CreateGroupMessageOrRequest_emails`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `CreateGroupMessageOrRequest_emails` (
  `CreateGroupMessageOrRequest_id` bigint(20) NOT NULL,
  `emails` varchar(255) DEFAULT NULL,
  KEY `FKEAE9CCAE569798BF` (`CreateGroupMessageOrRequest_id`),
  CONSTRAINT `FKEAE9CCAE569798BF` FOREIGN KEY (`CreateGroupMessageOrRequest_id`) REFERENCES `CreateGroupMessageOrRequest` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DATABASECHANGELOG`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOG` (
  `ID` varchar(255) NOT NULL,
  `AUTHOR` varchar(255) NOT NULL,
  `FILENAME` varchar(255) NOT NULL,
  `DATEEXECUTED` datetime NOT NULL,
  `ORDEREXECUTED` int(11) NOT NULL,
  `EXECTYPE` varchar(10) NOT NULL,
  `MD5SUM` varchar(35) DEFAULT NULL,
  `DESCRIPTION` varchar(255) DEFAULT NULL,
  `COMMENTS` varchar(255) DEFAULT NULL,
  `TAG` varchar(255) DEFAULT NULL,
  `LIQUIBASE` varchar(20) DEFAULT NULL,
  `CONTEXTS` varchar(255) DEFAULT NULL,
  `LABELS` varchar(255) DEFAULT NULL,
  `DEPLOYMENT_ID` varchar(10) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DATABASECHANGELOGLOCK`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DATABASECHANGELOGLOCK` (
  `ID` int(11) NOT NULL,
  `LOCKED` tinyint(1) NOT NULL,
  `LOCKGRANTED` datetime DEFAULT NULL,
  `LOCKEDBY` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DMPUser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DMPUser` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `timestamp` datetime NOT NULL,
  `dmpId` varchar(255) NOT NULL,
  `title` varchar(255) DEFAULT NULL,
  `dmpDownloadPdf_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKdp2ga3hjercu3v53tccaxhny9` (`user_id`),
  KEY `FKm61v8xbfrc3bdswkvq5sck0qs` (`dmpDownloadPdf_id`),
  CONSTRAINT `FKdp2ga3hjercu3v53tccaxhny9` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKm61v8xbfrc3bdswkvq5sck0qs` FOREIGN KEY (`dmpDownloadPdf_id`) REFERENCES `EcatDocumentFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DigitalObjectIdentifier`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DigitalObjectIdentifier` (
  `id` bigint(20) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `state` varchar(20) DEFAULT NULL,
  `identifier` varchar(255) DEFAULT NULL,
  `otherDataJsonString` longtext DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `publicLink` varchar(100) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `customFieldsOnPublicPage` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `isPublicLink` (`publicLink`),
  KEY `FK_3pv8g14ux6qm0qfuvelky5npq` (`subSample_id`),
  KEY `FK_gf4nr5rw2jkltcup0r4qej667` (`sample_id`),
  KEY `FK_gvu9ehdl7rd5mi84o28n3q17k` (`container_id`),
  CONSTRAINT `FK_3pv8g14ux6qm0qfuvelky5npq` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_gf4nr5rw2jkltcup0r4qej667` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_gvu9ehdl7rd5mi84o28n3q17k` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `DigitalObjectIdentifier_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `DigitalObjectIdentifier_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `state` varchar(20) DEFAULT NULL,
  `identifier` varchar(255) DEFAULT NULL,
  `otherDataJsonString` longtext DEFAULT NULL,
  `title` varchar(255) DEFAULT NULL,
  `publicLink` varchar(100) DEFAULT NULL,
  `type` int(11) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `customFieldsOnPublicPage` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_k3v8dw4wd7xr8trjoqfxwfwio` (`REV`),
  CONSTRAINT `FK_k3v8dw4wd7xr8trjoqfxwfwio` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
DROP TABLE IF EXISTS `EcatChemistryFile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatChemistryFile` (
  `id` bigint(20) NOT NULL,
  `chemString` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FK9FWKCTCEPGPI7PKPP78UYJFT2` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatChemistryFile_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatChemistryFile_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `chemString` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  CONSTRAINT `FKI5BE96PM6YW8ESF4TFB3098H4` FOREIGN KEY (`id`, `REV`) REFERENCES `EcatMediaFile_AUD` (`id`, `REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `id` bigint(20) NOT NULL,
  `thumbNail_id` bigint(20) DEFAULT NULL,
  `docThumbnailFP_id` bigint(20) DEFAULT NULL,
  `numThumbnailConversionAttemptsMade` tinyint(4) DEFAULT 0,
  PRIMARY KEY (`id`),
  KEY `FKE688C548C81E6316` (`id`),
  KEY `FKE688C5485C9543B2` (`thumbNail_id`),
  KEY `FK_nfva120vkiceqt406h2o9ie60` (`docThumbnailFP_id`),
  CONSTRAINT `FKE688C5485C9543B2` FOREIGN KEY (`thumbNail_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKE688C548C81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`),
  CONSTRAINT `FK_nfva120vkiceqt406h2o9ie60` FOREIGN KEY (`docThumbnailFP_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `EcatDocumentFile_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `EcatDocumentFile_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `documentType` varchar(255) DEFAULT NULL,
  `thumbNail_id` bigint(20) DEFAULT NULL,
  `docThumbnailFP_id` bigint(20) DEFAULT NULL,
  `numThumbnailConversionAttemptsMade` tinyint(4) DEFAULT 0,
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
  `rotation` tinyint(4) NOT NULL,
  `originalImage_id` bigint(20) DEFAULT NULL,
  `originalImageVersion` bigint(20) NOT NULL,
  `thumbnailImageFP_id` bigint(20) DEFAULT NULL,
  `workingImageFP_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF7EC9A6A20867C78` (`imageFileRezisedEditor_id`),
  KEY `FKF7EC9A6AC81E6316` (`id`),
  KEY `FKF7EC9A6A721017CE` (`imageThumbnailed_id`),
  KEY `FK_99l9v2esj1nigeas36flmg35u` (`originalImage_id`),
  KEY `FK_cnpq4e37k84gu9tu4jqw3kh8r` (`thumbnailImageFP_id`),
  KEY `FK_a7ohoy3e5xqulim2euam84ihi` (`workingImageFP_id`),
  CONSTRAINT `FKF7EC9A6A20867C78` FOREIGN KEY (`imageFileRezisedEditor_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKF7EC9A6A721017CE` FOREIGN KEY (`imageThumbnailed_id`) REFERENCES `ImageBlob` (`id`),
  CONSTRAINT `FKF7EC9A6AC81E6316` FOREIGN KEY (`id`) REFERENCES `EcatMediaFile` (`id`),
  CONSTRAINT `FK_99l9v2esj1nigeas36flmg35u` FOREIGN KEY (`originalImage_id`) REFERENCES `EcatImage` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_a7ohoy3e5xqulim2euam84ihi` FOREIGN KEY (`workingImageFP_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_cnpq4e37k84gu9tu4jqw3kh8r` FOREIGN KEY (`thumbnailImageFP_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `rotation` tinyint(4) NOT NULL,
  `thumbnailImageFP_id` bigint(20) DEFAULT NULL,
  `workingImageFP_id` bigint(20) DEFAULT NULL,
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
  `size` bigint(20) NOT NULL,
  `id` bigint(20) NOT NULL,
  `fileProperty_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`),
  KEY `FK55F3190FDF5C8EB5` (`id`),
  KEY `FK55F3190F43F411EE` (`fileProperty_id`),
  CONSTRAINT `FK55F3190F43F411EE` FOREIGN KEY (`fileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
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
  `size` bigint(20) DEFAULT NULL,
  `fileProperty_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) DEFAULT 1,
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
DROP TABLE IF EXISTS `ExternalStorageLocation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ExternalStorageLocation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `externalStorageId` bigint(20) NOT NULL,
  `operationDate` bigint(20) NOT NULL,
  `fileStore_id` bigint(20) NOT NULL,
  `connectedMediaFile_id` bigint(20) NOT NULL,
  `operationUser_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9091AB6ADF43265C` (`operationUser_id`),
  KEY `FK9091AB6ADF43287D` (`fileStore_id`),
  KEY `FK9091AB6ADF43244B` (`connectedMediaFile_id`),
  CONSTRAINT `FK9091AB6ADF43244B` FOREIGN KEY (`connectedMediaFile_id`) REFERENCES `EcatMediaFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK9091AB6ADF43265C` FOREIGN KEY (`operationUser_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK9091AB6ADF43287D` FOREIGN KEY (`fileStore_id`) REFERENCES `NfsFileStore` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ExtraField`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ExtraField` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime NOT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `name` varchar(255) NOT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_abc2mq2ffgr887lpofc0clsle` (`sample_id`),
  KEY `FK_def2mq2ffgr887lpofc0clsle` (`subSample_id`),
  KEY `FK_brv0sieqdjlvl16gon9bgsdie` (`container_id`),
  CONSTRAINT `FK_abc2mq2ffgr887lpofc0clsle` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_brv0sieqdjlvl16gon9bgsdie` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_def2mq2ffgr887lpofc0clsle` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ExtraField_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ExtraField_AUD` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_abca2d0hx85t1yfhqip6v3ne2` (`REV`),
  CONSTRAINT `FK_abca2d0hx85t1yfhqip6v3ne2` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `rtfData` longtext DEFAULT NULL,
  `fieldForm_id` bigint(20) NOT NULL,
  `structuredDocument_id` bigint(20) DEFAULT NULL,
  `tempField_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK40BB0DA5A86BE23` (`structuredDocument_id`),
  KEY `FK40BB0DA4FB44926` (`tempField_id`),
  KEY `FK_8hdhlfx9leu1is3omm43kcsls` (`fieldForm_id`),
  CONSTRAINT `FK40BB0DA4FB44926` FOREIGN KEY (`tempField_id`) REFERENCES `Field` (`id`),
  CONSTRAINT `FK40BB0DA5A86BE23` FOREIGN KEY (`structuredDocument_id`) REFERENCES `StructuredDocument` (`id`),
  CONSTRAINT `FK_8hdhlfx9leu1is3omm43kcsls` FOREIGN KEY (`fieldForm_id`) REFERENCES `FieldForm` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldAttachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldAttachment` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `field_id` bigint(20) NOT NULL,
  `mediafile_id` bigint(20) NOT NULL,
  `deleted` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2478505F3319B855` (`mediafile_id`),
  KEY `FK2878505F33C51952` (`field_id`),
  CONSTRAINT `FK2478505F3319B855` FOREIGN KEY (`mediafile_id`) REFERENCES `EcatMediaFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK2878505F33C51952` FOREIGN KEY (`field_id`) REFERENCES `Field` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=32772 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldAttachment_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldAttachment_AUD` (
  `id` int(11) NOT NULL,
  `REV` int(11) NOT NULL,
  `field_id` bigint(20) NOT NULL,
  `mediafile_id` bigint(20) NOT NULL,
  `REVTYPE` tinyint(3) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK1A3736B0DF74E053` (`REV`),
  KEY `FK2978505F3319B855` (`mediafile_id`),
  KEY `FK2978505F33C51952` (`field_id`),
  CONSTRAINT `FK1A3736B0DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldAutosaveLog`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldAutosaveLog` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `tempField_id` bigint(20) NOT NULL,
  `creationDate` datetime NOT NULL,
  `fieldData` longtext NOT NULL,
  `fieldID` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_Field_AutosaveLog_EE` (`fieldID`),
  CONSTRAINT `FK_Field_AutosaveLog_EE` FOREIGN KEY (`fieldID`) REFERENCES `Field` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldForm`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldForm` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `deleted` bit(1) NOT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `type` varchar(255) NOT NULL,
  `decimalPlaces` tinyint(4) DEFAULT NULL,
  `defaultNumberValue` double DEFAULT NULL,
  `maxNumberValue` double DEFAULT NULL,
  `minNumberValue` double DEFAULT NULL,
  `defaultValue` longtext DEFAULT NULL,
  `defaultStringValue` varchar(255) DEFAULT NULL,
  `ifPassword` bit(1) DEFAULT NULL,
  `defaultTime` bigint(20) DEFAULT NULL,
  `maxTime` bigint(20) DEFAULT NULL,
  `minTime` bigint(20) DEFAULT NULL,
  `timeFormat` varchar(255) DEFAULT NULL,
  `choiceOptions` varchar(1000) DEFAULT NULL,
  `defaultChoiceOption` varchar(255) DEFAULT NULL,
  `multipleChoice` bit(1) DEFAULT NULL,
  `defaultRadioOption` varchar(255) DEFAULT NULL,
  `radioOption` longtext DEFAULT NULL,
  `defaultDate` bigint(20) DEFAULT NULL,
  `format` varchar(255) DEFAULT NULL,
  `max_value` bigint(20) DEFAULT NULL,
  `minValue` bigint(20) DEFAULT NULL,
  `form_id` bigint(20) NOT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `showAsPickList` bit(1) NOT NULL DEFAULT b'0',
  `sortAlphabetic` bit(1) NOT NULL DEFAULT b'0',
  `mandatory` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_11o2xwhsjotg0b837vv4ericn` (`form_id`),
  KEY `FK_h7tb5wul51s7k0npnj6awa1ko` (`tempFieldForm_id`),
  CONSTRAINT `FK_11o2xwhsjotg0b837vv4ericn` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_h7tb5wul51s7k0npnj6awa1ko` FOREIGN KEY (`tempFieldForm_id`) REFERENCES `FieldForm` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FieldForm_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FieldForm_AUD` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `type` varchar(255) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `tempFieldForm_id` bigint(20) DEFAULT NULL,
  `defaultRadioOption` varchar(255) DEFAULT NULL,
  `radioOption` longtext DEFAULT NULL,
  `choiceOptions` varchar(1000) DEFAULT NULL,
  `defaultChoiceOption` varchar(255) DEFAULT NULL,
  `multipleChoice` bit(1) DEFAULT NULL,
  `defaultTime` bigint(20) DEFAULT NULL,
  `maxTime` bigint(20) DEFAULT NULL,
  `minTime` bigint(20) DEFAULT NULL,
  `timeFormat` varchar(255) DEFAULT NULL,
  `defaultValue` longtext DEFAULT NULL,
  `defaultDate` bigint(20) DEFAULT NULL,
  `format` varchar(255) DEFAULT NULL,
  `max_value` bigint(20) DEFAULT NULL,
  `minValue` bigint(20) DEFAULT NULL,
  `decimalPlaces` tinyint(4) DEFAULT NULL,
  `defaultNumberValue` double DEFAULT NULL,
  `maxNumberValue` double DEFAULT NULL,
  `minNumberValue` double DEFAULT NULL,
  `defaultStringValue` varchar(255) DEFAULT NULL,
  `ifPassword` bit(1) DEFAULT NULL,
  `showAsPickList` bit(1) NOT NULL DEFAULT b'0',
  `sortAlphabetic` bit(1) NOT NULL DEFAULT b'0',
  `mandatory` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_7ib6fp0gh55vb0w0oyf0emua7` (`REV`),
  CONSTRAINT `FK_7ib6fp0gh55vb0w0oyf0emua7` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `rtfData` longtext DEFAULT NULL,
  `data` varchar(1000) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKC2DFE1ABDF74E053` (`REV`),
  KEY `FK_Field_AUD_structuredDocument_id` (`structuredDocument_id`),
  CONSTRAINT `FKC2DFE1ABDF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`),
  CONSTRAINT `FK_Field_AUD_structuredDocument_id` FOREIGN KEY (`structuredDocument_id`) REFERENCES `StructuredDocument` (`id`) ON DELETE CASCADE ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FileProperty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FileProperty` (
  `createDate` date DEFAULT NULL,
  `fileCategory` varchar(255) DEFAULT NULL,
  `fileGroup` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileOwner` varchar(255) DEFAULT NULL,
  `fileSize` varchar(255) DEFAULT NULL,
  `fileUser` varchar(255) DEFAULT NULL,
  `fileVersion` varchar(255) DEFAULT NULL,
  `updateDate` date DEFAULT NULL,
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `relPath` varchar(500) NOT NULL,
  `root_id` bigint(20) DEFAULT NULL,
  `external` bit(1) NOT NULL DEFAULT b'0',
  `contentsHash` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK83044F9131DE45AB` (`root_id`),
  KEY `contentsHash_idx` (`contentsHash`),
  CONSTRAINT `FK83044F9131DE45AB` FOREIGN KEY (`root_id`) REFERENCES `FileStoreRoot` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=104 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FileStoreRoot`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FileStoreRoot` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `creationDate` datetime DEFAULT NULL,
  `current` bit(1) NOT NULL,
  `fileStoreRoot` varchar(500) NOT NULL,
  `external` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `FileStoreRoot_Bk_spac964`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FileStoreRoot_Bk_spac964` (
  `id` bigint(20) NOT NULL DEFAULT 0,
  `creationDate` datetime DEFAULT NULL,
  `current` bit(1) NOT NULL,
  `fileStoreRoot` varchar(500) NOT NULL,
  `external` bit(1) NOT NULL DEFAULT b'0'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Folder`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Folder` (
  `systemFolder` bit(1) NOT NULL,
  `id` bigint(20) NOT NULL,
  `docTag` varchar(8000) DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
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
  `docTag` varchar(8000) DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
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
DROP TABLE IF EXISTS `FormUserMenu`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `FormUserMenu` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `formStableId` varchar(255) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK878958CEDE6F978E` (`user_id`),
  CONSTRAINT `FK878958CEDE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=32774 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `GroupMembershipEvent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GroupMembershipEvent` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  `groupEventType` varchar(30) DEFAULT NULL,
  `timestamp` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_roowcxdv9gq4rhhvh5jlewrcu` (`user_id`),
  KEY `FK_akedqhy4imflue8kdtvfg5ova` (`group_id`),
  CONSTRAINT `FK_akedqhy4imflue8kdtvfg5ova` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_roowcxdv9gq4rhhvh5jlewrcu` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `GroupMessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `GroupMessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` longtext DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
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
  CONSTRAINT `FKB5828B641CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`),
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305b5828b64` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305b5828b64` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `IconEntity`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `IconEntity` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `height` int(11) NOT NULL,
  `iconImage` mediumblob DEFAULT NULL,
  `imgName` varchar(255) DEFAULT NULL,
  `imgType` varchar(255) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `width` int(11) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=13 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ImageBlob`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ImageBlob` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `data` longblob DEFAULT NULL,
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
  `data` longblob DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK595DF949DF74E053` (`REV`),
  CONSTRAINT `FK595DF949DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `InternalLink`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InternalLink` (
  `id` bigint(20) NOT NULL,
  `source_id` bigint(20) DEFAULT NULL,
  `target_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_lq40ralcejodtraeiyu79tphf` (`source_id`),
  KEY `FK_rwgehvaqps4ocx97lvjcwesu1` (`target_id`),
  CONSTRAINT `FK_lq40ralcejodtraeiyu79tphf` FOREIGN KEY (`source_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_rwgehvaqps4ocx97lvjcwesu1` FOREIGN KEY (`target_id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `InventoryChoiceFieldDef`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InventoryChoiceFieldDef` (
  `id` bigint(20) NOT NULL,
  `choiceOptions` text DEFAULT NULL,
  `multipleChoice` bit(1) NOT NULL DEFAULT b'0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `InventoryFile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InventoryFile` (
  `id` bigint(20) NOT NULL,
  `contentMimeType` varchar(255) DEFAULT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `deleted` bit(1) NOT NULL,
  `extension` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) NOT NULL,
  `fileType` tinyint(4) DEFAULT NULL,
  `size` bigint(20) NOT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `fileProperty_id` bigint(20) DEFAULT NULL,
  `sampleField_id` bigint(20) DEFAULT NULL,
  `mediaFile_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_60r1vytsrnf4b8os2mts1285p` (`sample_id`),
  KEY `FK_d2y9fd765i7uou52jtqhmruga` (`container_id`),
  KEY `FK_fjyr0bfuhxljl0q49wg59ku1b` (`subSample_id`),
  KEY `FK_2uehkfgpm1fxu504mnlkaqek5` (`fileProperty_id`),
  KEY `FK_qy05jb7phinbki3tip3q5okdj` (`sampleField_id`),
  KEY `FK_o7iqfibrftmcfpvwyxvygsn3g` (`mediaFile_id`),
  CONSTRAINT `FK_2uehkfgpm1fxu504mnlkaqek5` FOREIGN KEY (`fileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_60r1vytsrnf4b8os2mts1285p` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_d2y9fd765i7uou52jtqhmruga` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_fjyr0bfuhxljl0q49wg59ku1b` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_o7iqfibrftmcfpvwyxvygsn3g` FOREIGN KEY (`mediaFile_id`) REFERENCES `EcatMediaFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_qy05jb7phinbki3tip3q5okdj` FOREIGN KEY (`sampleField_id`) REFERENCES `SampleField` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `InventoryFile_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InventoryFile_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `contentMimeType` varchar(255) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `extension` varchar(255) DEFAULT NULL,
  `fileName` varchar(255) DEFAULT NULL,
  `fileType` tinyint(4) DEFAULT NULL,
  `size` bigint(20) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `fileProperty_id` bigint(20) DEFAULT NULL,
  `sampleField_id` bigint(20) DEFAULT NULL,
  `mediaFile_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_hejjvjabiwyowoerr70e12put` (`REV`),
  CONSTRAINT `FK_hejjvjabiwyowoerr70e12put` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `InventoryRadioFieldDef`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `InventoryRadioFieldDef` (
  `id` bigint(20) NOT NULL,
  `radioOptions` text DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ListOfMaterials`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ListOfMaterials` (
  `id` bigint(20) NOT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `elnField_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_2sny6f4vcmhuc487mm7y4js61` (`elnField_id`),
  CONSTRAINT `FK_2sny6f4vcmhuc487mm7y4js61` FOREIGN KEY (`elnField_id`) REFERENCES `Field` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ListOfMaterials_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ListOfMaterials_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `elnField_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_f260rgplm38ve0wibnm6reybq` (`REV`),
  CONSTRAINT `FK_f260rgplm38ve0wibnm6reybq` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `MaterialUsage`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MaterialUsage` (
  `id` bigint(20) NOT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `parentLom_id` bigint(20) NOT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_45vkry2175qfa5posl3ink1ia` (`parentLom_id`),
  KEY `FK_flesvypjh0nv7n46jo9hj822a` (`container_id`),
  KEY `FK_gvk1jr0lilnthxwkf7b4liabl` (`sample_id`),
  KEY `FK_e40ck7j8jm804tu8xnjgpci5v` (`subSample_id`),
  CONSTRAINT `FK_45vkry2175qfa5posl3ink1ia` FOREIGN KEY (`parentLom_id`) REFERENCES `ListOfMaterials` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_e40ck7j8jm804tu8xnjgpci5v` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_flesvypjh0nv7n46jo9hj822a` FOREIGN KEY (`container_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_gvk1jr0lilnthxwkf7b4liabl` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `MaterialUsage_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MaterialUsage_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `container_id` bigint(20) DEFAULT NULL,
  `parentLom_id` bigint(20) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_9pj30wcfiujc6w1bbrmha6noh` (`REV`),
  CONSTRAINT `FK_9pj30wcfiujc6w1bbrmha6noh` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `MessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `MessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` longtext DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
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
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `NfsFileStore`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `NfsFileStore` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(255) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `path` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `fileSystem_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK60DF32F7DE6F978E` (`user_id`),
  KEY `FK9091AD6ADF43272B` (`fileSystem_id`),
  CONSTRAINT `FK60DF32F7DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK9091AD6ADF43272B` FOREIGN KEY (`fileSystem_id`) REFERENCES `NfsFileSystem` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `NfsFileSystem`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `NfsFileSystem` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `authType` varchar(255) DEFAULT NULL,
  `clientType` varchar(255) DEFAULT NULL,
  `disabled` bit(1) NOT NULL,
  `name` varchar(255) DEFAULT NULL,
  `url` varchar(255) DEFAULT NULL,
  `authOptions` longtext DEFAULT NULL,
  `clientOptions` longtext DEFAULT NULL,
  PRIMARY KEY (`id`)
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
  `message` longtext DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `notificationMessage` varchar(2000) DEFAULT NULL,
  `notificationType` int(11) DEFAULT NULL,
  `notificationData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKF1E9FFB664CCD43D2d45dd0b` (`originator_id`),
  KEY `FKF1E9FFB6CFF3DF142d45dd0b` (`record_id`),
  CONSTRAINT `FKF1E9FFB664CCD43D2d45dd0b` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKF1E9FFB6CFF3DF142d45dd0b` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `OAuthApp`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `OAuthApp` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  `clientId` varchar(100) NOT NULL,
  `clientSecret` varchar(64) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `clientId` (`clientId`),
  UNIQUE KEY `clientSecret` (`clientSecret`),
  KEY `FK_7rts1nhknixdema78tidkk7ds` (`user_id`),
  CONSTRAINT `FK_7rts1nhknixdema78tidkk7ds` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `OAuthToken`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `OAuthToken` (
  `id` bigint(20) NOT NULL,
  `clientId` varchar(100) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  `accessToken` varchar(64) DEFAULT NULL,
  `scope` varchar(255) NOT NULL,
  `expiryTime` datetime DEFAULT NULL,
  `refreshToken` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `accessToken` (`accessToken`),
  UNIQUE KEY `user_id` (`user_id`,`clientId`),
  UNIQUE KEY `refreshToken` (`refreshToken`),
  CONSTRAINT `FK_6rts1nhknixdema78tidkk7ds` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `OfflineRecordUser`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `OfflineRecordUser` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `creationDate` datetime DEFAULT NULL,
  `workType` int(11) NOT NULL,
  `record_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK5E74A5DFCFF3DF14` (`record_id`),
  KEY `FK5E74A5DFDE6F978E` (`user_id`),
  CONSTRAINT `FK5E74A5DFCFF3DF14` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK5E74A5DFDE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Organisation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Organisation` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `approved` bit(1) NOT NULL,
  `title` varchar(191) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_Org_title` (`title`)
) ENGINE=InnoDB AUTO_INCREMENT=3843 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `PropertyDescriptor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `PropertyDescriptor` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `defaultValue` varchar(255) NOT NULL,
  `name` varchar(50) NOT NULL,
  `type` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=84 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `REVINFO`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `REVINFO` (
  `REV` int(11) NOT NULL AUTO_INCREMENT,
  `REVTSTMP` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`REV`)
) ENGINE=InnoDB AUTO_INCREMENT=37 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSChemElement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSChemElement` (
  `id` bigint(20) NOT NULL,
  `chemElements` longtext DEFAULT NULL,
  `dataImage` longblob DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `rgroupId` int(11) DEFAULT NULL,
  `smilesString` longtext DEFAULT NULL,
  `chemId` int(11) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `chemElementsFormat` varchar(32) NOT NULL,
  `reactionId` int(11) DEFAULT NULL,
  `creationDate` datetime NOT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `ecatChemFileId` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKD43D0D6E1A94D963` (`record_id`),
  KEY `FK_58261u0q3tvd7kegdeek8boas` (`imageFileProperty_id`),
  KEY `FKDHG5HFGSH3SDG038` (`ecatChemFileId`),
  CONSTRAINT `FKD43D0D6E1A94D963` FOREIGN KEY (`record_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKDHG5HFGSH3SDG038` FOREIGN KEY (`ecatChemFileId`) REFERENCES `EcatChemistryFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_58261u0q3tvd7kegdeek8boas` FOREIGN KEY (`imageFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSChemElement_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSChemElement_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `chemElements` longtext DEFAULT NULL,
  `dataImage` longblob DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `rgroupId` int(11) DEFAULT NULL,
  `smilesString` longtext DEFAULT NULL,
  `chemId` int(11) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `chemElementsFormat` varchar(32) NOT NULL,
  `reactionId` int(11) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `ecatChemFileId` bigint(20) DEFAULT NULL,
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
  `formType` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) NOT NULL,
  `publishingState` varchar(255) DEFAULT NULL,
  `stableId` varchar(50) NOT NULL,
  `tmpTag` varchar(255) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `previousVersion_id` bigint(20) DEFAULT NULL,
  `tempForm_id` bigint(20) DEFAULT NULL,
  `systemForm` bit(1) NOT NULL,
  `temporary` bit(1) NOT NULL,
  `DTYPE` varchar(31) NOT NULL,
  `defaultUnitId` int(11) DEFAULT NULL,
  `subSampleName` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `stableid` (`stableId`),
  KEY `FK90A082A5813663E7` (`previousVersion_id`),
  KEY `FK90A082A54A5647A6` (`owner_id`),
  KEY `FK90A082A522322FF0` (`tempForm_id`),
  KEY `issystem` (`systemForm`),
  CONSTRAINT `FK90A082A522322FF0` FOREIGN KEY (`tempForm_id`) REFERENCES `RSForm` (`id`),
  CONSTRAINT `FK90A082A54A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK90A082A5813663E7` FOREIGN KEY (`previousVersion_id`) REFERENCES `RSForm` (`id`)
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
  `formType` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `publishingState` varchar(255) DEFAULT NULL,
  `stableID` varchar(255) DEFAULT NULL,
  `tmpTag` varchar(255) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `previousVersion_id` bigint(20) DEFAULT NULL,
  `tempForm_id` bigint(20) DEFAULT NULL,
  `systemForm` bit(1) DEFAULT NULL,
  `temporary` bit(1) DEFAULT NULL,
  `DTYPE` varchar(31) NOT NULL,
  `defaultUnitId` int(11) DEFAULT NULL,
  `subSampleName` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK71D0D5F6DF74E053` (`REV`),
  CONSTRAINT `FK71D0D5F6DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSMath`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSMath` (
  `id` bigint(20) NOT NULL,
  `field_id` bigint(20) DEFAULT NULL,
  `mathSvg_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latex` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_53o818esycj250k9vfq43bwo1` (`field_id`),
  KEY `FK_m6l20a3fxxhv6dxk0ksajfac3` (`record_id`),
  KEY `FK_pdojjawkeij3j15t9gxf4910h` (`mathSvg_id`),
  CONSTRAINT `FK_53o818esycj250k9vfq43bwo1` FOREIGN KEY (`field_id`) REFERENCES `Field` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_m6l20a3fxxhv6dxk0ksajfac3` FOREIGN KEY (`record_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_pdojjawkeij3j15t9gxf4910h` FOREIGN KEY (`mathSvg_id`) REFERENCES `ImageBlob` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RSMath_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RSMath_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `field_id` bigint(20) DEFAULT NULL,
  `mathSvg_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latex` varchar(2000) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_m3cpaywqgdl7f5wygnfcu2o3k` (`REV`),
  CONSTRAINT `FK_m3cpaywqgdl7f5wygnfcu2o3k` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
DROP TABLE IF EXISTS `Record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Record` (
  `id` bigint(20) NOT NULL,
  `tempRecord_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK91AB587194BB9466` (`id`),
  KEY `FK91AB58717C8BA40F` (`tempRecord_id`),
  CONSTRAINT `FK91AB58717C8BA40F` FOREIGN KEY (`tempRecord_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK91AB587194BB9466` FOREIGN KEY (`id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordAttachment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordAttachment` (
  `id` bigint(20) NOT NULL,
  `mediaFile_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK101974741A94D963` (`record_id`),
  KEY `FK101974743319B855` (`mediaFile_id`),
  CONSTRAINT `FK101974741A94D963` FOREIGN KEY (`record_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK101974743319B855` FOREIGN KEY (`mediaFile_id`) REFERENCES `EcatMediaFile` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordAttachment_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordAttachment_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `mediaFile_id` bigint(20) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FKC4CE4845DF74E053` (`REV`),
  CONSTRAINT `FKC4CE4845DF74E053` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordGroupSharing`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordGroupSharing` (
  `id` bigint(20) NOT NULL,
  `shared_id` bigint(20) DEFAULT NULL,
  `sharee_id` bigint(20) DEFAULT NULL,
  `targetFolder_id` bigint(20) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `publicLink` varchar(100) DEFAULT NULL,
  `sharedBy_id` bigint(20) DEFAULT NULL,
  `publicationSummary` varchar(255) DEFAULT NULL,
  `displayContactDetails` bit(1) DEFAULT NULL,
  `publishOnInternet` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `isPublicLink` (`publicLink`),
  KEY `FK2AA5A06E3676B680` (`shared_id`),
  KEY `FK2AA5A06EF5ADC9D2` (`targetFolder_id`),
  KEY `FK_RGS_USR` (`sharedBy_id`),
  CONSTRAINT `FK2AA5A06E3676B680` FOREIGN KEY (`shared_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FK2AA5A06EF5ADC9D2` FOREIGN KEY (`targetFolder_id`) REFERENCES `Folder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_RGS_USR` FOREIGN KEY (`sharedBy_id`) REFERENCES `User` (`id`) ON DELETE CASCADE ON UPDATE CASCADE
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
  `deletedDate` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK3280CA5AEAE40843` (`folder_id`),
  KEY `FK3280CA5ACFF3DF14` (`record_id`),
  CONSTRAINT `FK3280CA5ACFF3DF14` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`),
  CONSTRAINT `FK3280CA5AEAE40843` FOREIGN KEY (`folder_id`) REFERENCES `Folder` (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=202 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `RecordUserFavorites`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `RecordUserFavorites` (
  `id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `record_id` (`record_id`,`user_id`),
  KEY `FKE6B089DBDE6F978E` (`user_id`),
  CONSTRAINT `FKE6B089DBCFF3DF14` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKE6B089DBDE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FKA2A3E5C231EEDD5A` FOREIGN KEY (`id`, `REV`) REFERENCES `BaseRecord_AUD` (`id`, `REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
DROP TABLE IF EXISTS `SD_AUD_TEMP`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SD_AUD_TEMP` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `deltaString` varchar(2000) DEFAULT NULL,
  `docTag` varchar(255) DEFAULT NULL,
  `temporaryDoc` bit(1) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Sample`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Sample` (
  `id` bigint(20) NOT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime NOT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `name` varchar(255) NOT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `storageTempMinNumericValue` decimal(19,3) DEFAULT NULL,
  `storageTempMinUnitId` int(11) DEFAULT NULL,
  `storageTempMaxNumericValue` decimal(19,3) DEFAULT NULL,
  `storageTempMaxUnitId` int(11) DEFAULT NULL,
  `owner_id` bigint(20) NOT NULL,
  `sampleSource` varchar(20) NOT NULL DEFAULT 'LAB_CREATED',
  `deletedDate` datetime DEFAULT NULL,
  `activeSubSamplesCount` int(11) NOT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `template` bit(1) DEFAULT b'0',
  `currMaxColIndex` int(11) DEFAULT NULL,
  `defaultUnitId` int(11) NOT NULL,
  `STemplate_id` bigint(20) DEFAULT NULL,
  `expiryDate` date DEFAULT NULL,
  `version` bigint(20) NOT NULL,
  `STemplateLinkedVersion` bigint(20) DEFAULT NULL,
  `subSampleName` varchar(30) NOT NULL,
  `subSampleNamePlural` varchar(30) NOT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_pysrs6dsf4fd920t4o34u4t4p` (`owner_id`),
  KEY `FK_e1gbE89odqh5aj0x65vj8c8en` (`imageFileProperty_id`),
  KEY `FK_kc5u6Gx28vg4lqxrpk1buksrt` (`thumbnailFileProperty_id`),
  KEY `FK_e92ss3qnyo5ln5tkjaxx5posr` (`STemplate_id`),
  CONSTRAINT `FK_e1gbE89odqh5aj0x65vj8c8en` FOREIGN KEY (`imageFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_e92ss3qnyo5ln5tkjaxx5posr` FOREIGN KEY (`STemplate_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_kc5u6Gx28vg4lqxrpk1buksrt` FOREIGN KEY (`thumbnailFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_pysrs6dsf4fd920t4o34u4t4p` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SampleField`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SampleField` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `columnIndex` int(11) NOT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) NOT NULL,
  `data` longtext DEFAULT NULL,
  `sample_id` bigint(20) NOT NULL,
  `choiceDef_id` bigint(20) DEFAULT NULL,
  `radioDef_id` bigint(20) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  `deleted` bit(1) NOT NULL,
  `templateField_id` bigint(20) DEFAULT NULL,
  `deleteOnSampleUpdate` bit(1) NOT NULL,
  `mandatory` bit(1) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_gev2mq2ffgr887lpofc0clsle` (`sample_id`),
  KEY `FK_l2rp030j0610hbbaavvpmrvlf` (`choiceDef_id`),
  KEY `FK_kvhedr5d789vej9pqnero2w1` (`radioDef_id`),
  KEY `FK_3y0km9bpfi2oxanow35t16fdq` (`templateField_id`),
  CONSTRAINT `FK_3y0km9bpfi2oxanow35t16fdq` FOREIGN KEY (`templateField_id`) REFERENCES `SampleField` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_gev2mq2ffgr887lpofc0clsle` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_kvhedr5d789vej9pqnero2w1` FOREIGN KEY (`radioDef_id`) REFERENCES `InventoryRadioFieldDef` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_l2rp030j0610hbbaavvpmrvlf` FOREIGN KEY (`choiceDef_id`) REFERENCES `InventoryChoiceFieldDef` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SampleField_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SampleField_AUD` (
  `DTYPE` varchar(31) NOT NULL,
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `columnIndex` int(11) DEFAULT NULL,
  `modificationDate` bigint(20) DEFAULT NULL,
  `name` varchar(50) DEFAULT NULL,
  `data` longtext DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `choiceDef_id` bigint(20) DEFAULT NULL,
  `radioDef_id` bigint(20) DEFAULT NULL,
  `type` varchar(20) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `templateField_id` bigint(20) DEFAULT NULL,
  `deleteOnSampleUpdate` bit(1) DEFAULT NULL,
  `mandatory` bit(1) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_ro1a2d0hx85t1yfhqip6v3ne2` (`REV`),
  CONSTRAINT `FK_ro1a2d0hx85t1yfhqip6v3ne2` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Sample_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Sample_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `storageTempMinNumericValue` decimal(19,3) DEFAULT NULL,
  `storageTempMinUnitId` int(11) DEFAULT NULL,
  `storageTempMaxNumericValue` decimal(19,3) DEFAULT NULL,
  `storageTempMaxUnitId` int(11) DEFAULT NULL,
  `owner_id` bigint(20) DEFAULT NULL,
  `sampleSource` varchar(20) DEFAULT 'LAB_CREATED',
  `deletedDate` datetime DEFAULT NULL,
  `activeSubSamplesCount` int(11) DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `template` bit(1) DEFAULT b'0',
  `currMaxColIndex` int(11) DEFAULT NULL,
  `defaultUnitId` int(11) DEFAULT NULL,
  `STemplate_id` bigint(20) DEFAULT NULL,
  `expiryDate` date DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `STemplateLinkedVersion` bigint(20) DEFAULT NULL,
  `subSampleName` varchar(30) DEFAULT NULL,
  `subSampleNamePlural` varchar(30) DEFAULT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_54n3aqp8aacp5ppt4jviu9pud` (`REV`),
  CONSTRAINT `FK_54n3aqp8aacp5ppt4jviu9pud` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ScheduledMaintenance`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ScheduledMaintenance` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `endDate` datetime NOT NULL,
  `message` varchar(255) DEFAULT NULL,
  `startDate` datetime NOT NULL,
  `stopUserLoginDate` datetime NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ShareRecordMessageOrRequest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ShareRecordMessageOrRequest` (
  `id` bigint(20) NOT NULL,
  `creationTime` datetime NOT NULL,
  `message` longtext DEFAULT NULL,
  `priority` int(11) DEFAULT NULL,
  `subject` varchar(255) DEFAULT NULL,
  `originator_id` bigint(20) NOT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  `latest` bit(1) NOT NULL,
  `messageType` int(11) NOT NULL,
  `requestedCompletionDate` datetime DEFAULT NULL,
  `status` int(11) DEFAULT NULL,
  `terminationTime` datetime DEFAULT NULL,
  `next_id` bigint(20) DEFAULT NULL,
  `previous_id` bigint(20) DEFAULT NULL,
  `permission` varchar(255) DEFAULT NULL,
  `target_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK883FEFF5B697F408` (`target_id`),
  KEY `FKF1E9FFB664CCD43Dc3272305883feff5` (`originator_id`),
  KEY `FKF1E9FFB6CFF3DF14c3272305883feff5` (`record_id`),
  CONSTRAINT `FK883FEFF5B697F408` FOREIGN KEY (`target_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKF1E9FFB664CCD43Dc3272305883feff5` FOREIGN KEY (`originator_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKF1E9FFB6CFF3DF14c3272305883feff5` FOREIGN KEY (`record_id`) REFERENCES `BaseRecord` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FKB76FB898276963CF` FOREIGN KEY (`signer_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FKB76FB8988CFABD87` FOREIGN KEY (`recordSigned_id`) REFERENCES `Record` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SignatureHash`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SignatureHash` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `hexValue` varchar(64) NOT NULL,
  `type` varchar(20) NOT NULL,
  `file_id` bigint(20) DEFAULT NULL,
  `signature_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9153E1C68E86C986` (`signature_id`),
  KEY `FK9153E1C6A6517FE3` (`file_id`),
  CONSTRAINT `FK9153E1C68E86C986` FOREIGN KEY (`signature_id`) REFERENCES `Signature` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK9153E1C6A6517FE3` FOREIGN KEY (`file_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Snippet`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Snippet` (
  `content` longtext DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `FKE85688FDDF5C8EB5` FOREIGN KEY (`id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Snippet_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Snippet_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `content` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  CONSTRAINT `FK57EBF04EF3DC1829` FOREIGN KEY (`id`, `REV`) REFERENCES `Record_AUD` (`id`, `REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `StructuredDocument`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `StructuredDocument` (
  `deltaString` varchar(2000) DEFAULT NULL,
  `docTag` varchar(8000) DEFAULT NULL,
  `temporaryDoc` bit(1) NOT NULL,
  `version` bigint(20) DEFAULT NULL,
  `id` bigint(20) NOT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `template_id` bigint(20) DEFAULT NULL,
  `allFieldsValid` bit(1) NOT NULL DEFAULT b'1',
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8D9991ACDF5C8EB5` (`id`),
  KEY `FK8D9991ACB5F0AA44` (`form_id`),
  KEY `FK23FEB76EDE6F978E` (`template_id`),
  CONSTRAINT `FK23FEB76EDE6F978E` FOREIGN KEY (`template_id`) REFERENCES `StructuredDocument` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
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
  `deltaString` varchar(2000) DEFAULT NULL,
  `docTag` varchar(8000) DEFAULT NULL,
  `temporaryDoc` bit(1) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `form_id` bigint(20) DEFAULT NULL,
  `template_id` bigint(20) DEFAULT NULL,
  `allFieldsValid` bit(1) DEFAULT b'1',
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK1E0D097DF3DC1829` (`id`,`REV`),
  KEY `FK_StructuredDocument_AUD_form_id` (`form_id`),
  CONSTRAINT `FK1E0D097DF3DC1829` FOREIGN KEY (`id`, `REV`) REFERENCES `Record_AUD` (`id`, `REV`),
  CONSTRAINT `FK_StructuredDocument_AUD_form_id` FOREIGN KEY (`form_id`) REFERENCES `RSForm` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SubSample`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SubSample` (
  `id` bigint(20) NOT NULL,
  `createdBy` varchar(255) NOT NULL,
  `creationDate` datetime NOT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime NOT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) NOT NULL,
  `deleted` bit(1) NOT NULL DEFAULT b'0',
  `name` varchar(255) NOT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) NOT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `sample_id` bigint(20) NOT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `parentLocation_id` bigint(20) DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) NOT NULL,
  `lastNonWorkbenchParent_id` bigint(20) DEFAULT NULL,
  `lastMoveDate` datetime(6) DEFAULT NULL,
  `deletedOnSampleDeletion` bit(1) NOT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_e4hsm7ilmshfbsuypo2w42mpg` (`sample_id`),
  KEY `FK_h4ked9aepxh7jw4674j3hap7b` (`parentLocation_id`),
  KEY `FK_e1gbE89odqh5aj0x65vj8c8em` (`imageFileProperty_id`),
  KEY `FK_kc5u6Gx28vg4lqxrpk1buksru` (`thumbnailFileProperty_id`),
  KEY `FK_6jx466ansycaun811xewjlrww` (`lastNonWorkbenchParent_id`),
  CONSTRAINT `FK_6jx466ansycaun811xewjlrww` FOREIGN KEY (`lastNonWorkbenchParent_id`) REFERENCES `Container` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_e1gbE89odqh5aj0x65vj8c8em` FOREIGN KEY (`imageFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_e4hsm7ilmshfbsuypo2w42mpg` FOREIGN KEY (`sample_id`) REFERENCES `Sample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_h4ked9aepxh7jw4674j3hap7b` FOREIGN KEY (`parentLocation_id`) REFERENCES `ContainerLocation` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_kc5u6Gx28vg4lqxrpk1buksru` FOREIGN KEY (`thumbnailFileProperty_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SubSampleNote`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SubSampleNote` (
  `id` bigint(20) NOT NULL,
  `content` varchar(2000) DEFAULT NULL,
  `creationDateMillis` bigint(20) NOT NULL,
  `subSample_id` bigint(20) NOT NULL,
  `createdBy_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_8t3xc4kyxfg32jesaql72bgvb` (`subSample_id`),
  KEY `FK_jbgv2cc1pl4wtntce829vdo2c` (`createdBy_id`),
  CONSTRAINT `FK_8t3xc4kyxfg32jesaql72bgvb` FOREIGN KEY (`subSample_id`) REFERENCES `SubSample` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_jbgv2cc1pl4wtntce829vdo2c` FOREIGN KEY (`createdBy_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SubSampleNote_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SubSampleNote_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `content` varchar(2000) DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `subSample_id` bigint(20) DEFAULT NULL,
  `createdBy_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_mennfe30g96wkivb3108yd0be` (`REV`),
  CONSTRAINT `FK_mennfe30g96wkivb3108yd0be` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SubSample_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SubSample_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `createdBy` varchar(255) DEFAULT NULL,
  `creationDate` datetime DEFAULT NULL,
  `creationDateMillis` bigint(20) DEFAULT NULL,
  `description` varchar(250) DEFAULT NULL,
  `modificationDate` datetime DEFAULT NULL,
  `modificationDateMillis` bigint(20) DEFAULT NULL,
  `modifiedBy` varchar(255) DEFAULT NULL,
  `deleted` bit(1) DEFAULT NULL,
  `name` varchar(255) DEFAULT NULL,
  `iconId` bigint(20) DEFAULT NULL,
  `quantityNumericValue` decimal(19,3) DEFAULT NULL,
  `quantityUnitId` int(11) DEFAULT NULL,
  `tags` varchar(8000) DEFAULT NULL,
  `sample_id` bigint(20) DEFAULT NULL,
  `deletedDate` datetime DEFAULT NULL,
  `parentLocation_id` bigint(20) DEFAULT NULL,
  `imageFileProperty_id` bigint(20) DEFAULT NULL,
  `thumbnailFileProperty_id` bigint(20) DEFAULT NULL,
  `version` bigint(20) DEFAULT NULL,
  `lastNonWorkbenchParent_id` bigint(20) DEFAULT NULL,
  `lastMoveDate` datetime(6) DEFAULT NULL,
  `deletedOnSampleDeletion` bit(1) DEFAULT NULL,
  `sharingMode` int(11) DEFAULT 0,
  `acl` longtext DEFAULT NULL,
  `tagMetaData` longtext DEFAULT NULL,
  PRIMARY KEY (`id`,`REV`),
  KEY `FK_hhi3kb4ejcmq9ivuua5iacjyx` (`REV`),
  CONSTRAINT `FK_hhi3kb4ejcmq9ivuua5iacjyx` FOREIGN KEY (`REV`) REFERENCES `REVINFO` (`REV`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SystemProperty`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SystemProperty` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `dependent_id` bigint(20) DEFAULT NULL,
  `descriptor_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK2E09E144471C3CE9` (`dependent_id`),
  KEY `FK2E09E144398CEDC3` (`descriptor_id`),
  CONSTRAINT `FK2E09E144398CEDC3` FOREIGN KEY (`descriptor_id`) REFERENCES `PropertyDescriptor` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK2E09E144471C3CE9` FOREIGN KEY (`dependent_id`) REFERENCES `SystemProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `SystemPropertyValue`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `SystemPropertyValue` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `value` varchar(255) NOT NULL,
  `property_id` bigint(20) DEFAULT NULL,
  `community_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9AEDEA8D79F8F253` (`property_id`),
  KEY `FK_drtsb215dfp02l875hmfseykv` (`community_id`),
  CONSTRAINT `FK9AEDEA8D79F8F253` FOREIGN KEY (`property_id`) REFERENCES `SystemProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_drtsb215dfp02l875hmfseykv` FOREIGN KEY (`community_id`) REFERENCES `Community` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=55 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `Thumbnail`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `Thumbnail` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `height` int(11) NOT NULL,
  `sourceId` bigint(20) DEFAULT NULL,
  `sourceType` int(11) DEFAULT NULL,
  `width` int(11) NOT NULL,
  `imageBlob_id` bigint(20) DEFAULT NULL,
  `revision` bigint(20) DEFAULT NULL,
  `sourceParentId` bigint(20) DEFAULT NULL,
  `rotation` tinyint(4) NOT NULL,
  `thumbnailFP_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKC6C070CC310145A6` (`imageBlob_id`),
  KEY `FK_hcmgu0neplficdj2d8mas7xw9` (`thumbnailFP_id`),
  CONSTRAINT `FKC6C070CC310145A6` FOREIGN KEY (`imageBlob_id`) REFERENCES `ImageBlob` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK_hcmgu0neplficdj2d8mas7xw9` FOREIGN KEY (`thumbnailFP_id`) REFERENCES `FileProperty` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `TokenBasedVerification`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `TokenBasedVerification` (
  `id` bigint(20) NOT NULL,
  `email` varchar(255) DEFAULT NULL,
  `ipAddressOfRequestor` varchar(255) DEFAULT NULL,
  `requestTime` timestamp NOT NULL DEFAULT current_timestamp() ON UPDATE current_timestamp(),
  `resetCompleted` bit(1) NOT NULL,
  `token` varchar(24) NOT NULL,
  `verificationType` tinyint(4) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `token` (`token`),
  KEY `FKA0614C95DE6F978E` (`user_id`),
  CONSTRAINT `FKA0614C95DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `salt` varchar(24) DEFAULT NULL,
  `username` varchar(50) NOT NULL,
  `rootFolder_id` bigint(20) DEFAULT NULL,
  `tempAccount` bit(1) NOT NULL DEFAULT b'0',
  `signupSource` varchar(25) DEFAULT 'MANUAL',
  `privateProfile` bit(1) DEFAULT NULL,
  `creationDate` datetime(6) DEFAULT NULL,
  `affiliation` varchar(255) DEFAULT 'n/a',
  `verificationPassword` varchar(255) DEFAULT NULL,
  `sid` varchar(255) DEFAULT NULL,
  `allowedPiRole` bit(1) NOT NULL DEFAULT b'0',
  `permissionStrings` mediumtext DEFAULT NULL,
  `tagsJsonString` varchar(255) DEFAULT NULL,
  `usernameAlias` varchar(50) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`),
  UNIQUE KEY `unique_usernameAlias` (`usernameAlias`),
  KEY `FK285FEBB7B465A1` (`rootFolder_id`),
  CONSTRAINT `FK285FEBB7B465A1` FOREIGN KEY (`rootFolder_id`) REFERENCES `Folder` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserAccountEvent`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserAccountEvent` (
  `id` bigint(20) NOT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `accountEventType` varchar(20) NOT NULL,
  `timestamp` datetime NOT NULL,
  PRIMARY KEY (`id`),
  KEY `FK_o4k690deu8830sfbf5j8gpws5` (`user_id`),
  CONSTRAINT `FK_o4k690deu8830sfbf5j8gpws5` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserApiKey`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserApiKey` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `apiKey` varchar(64) DEFAULT NULL,
  `created` datetime NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `unique_apikey` (`apiKey`),
  KEY `FKB6C3ADB0DE6F978E` (`user_id`),
  CONSTRAINT `FKB6C3ADB0DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserAppConfig`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserAppConfig` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `enabled` bit(1) NOT NULL,
  `app_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `user_id` (`user_id`,`app_id`),
  KEY `FKE27F8518C68E22F4` (`app_id`),
  CONSTRAINT `FKE27F8518C68E22F4` FOREIGN KEY (`app_id`) REFERENCES `App` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FKE27F8518DE6F9784` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB AUTO_INCREMENT=28 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserConnection`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserConnection` (
  `userId` varchar(50) NOT NULL,
  `providerId` varchar(50) NOT NULL,
  `providerUserId` varchar(50) NOT NULL,
  `rank` int(11) NOT NULL,
  `displayName` varchar(255) DEFAULT NULL,
  `profileUrl` varchar(512) DEFAULT NULL,
  `imageUrl` varchar(512) DEFAULT NULL,
  `accessToken` varchar(2048) DEFAULT NULL,
  `secret` varchar(2048) DEFAULT NULL,
  `refreshToken` varchar(2048) DEFAULT NULL,
  `expireTime` bigint(20) DEFAULT NULL,
  `encrypted` bit(1) DEFAULT b'0',
  PRIMARY KEY (`userId`,`providerId`,`providerUserId`),
  UNIQUE KEY `UserConnectionRank` (`userId`,`providerId`,`rank`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserGroup`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserGroup` (
  `id` bigint(20) NOT NULL,
  `includePermissions` bit(1) NOT NULL,
  `roleInGroup` int(11) DEFAULT NULL,
  `group_id` bigint(20) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  `adminViewDocsEnabled` bit(1) NOT NULL DEFAULT b'0',
  `piCanEditWork` tinyint(1) NOT NULL,
  `autoshareEnabled` bit(1) DEFAULT b'0',
  `autoShareFolder_id` bigint(20) DEFAULT NULL,
  `permissionStrings` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK8A5BE154DE6F978E` (`user_id`),
  KEY `FK8A5BE1541CC96626` (`group_id`),
  KEY `FK_n9s3bs7e3g2qq8lj772fwc4y1` (`autoShareFolder_id`),
  CONSTRAINT `FK8A5BE1541CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`),
  CONSTRAINT `FK8A5BE154DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`),
  CONSTRAINT `FK_n9s3bs7e3g2qq8lj772fwc4y1` FOREIGN KEY (`autoShareFolder_id`) REFERENCES `Folder` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserKeyPair`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserKeyPair` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `privateKey` longtext DEFAULT NULL,
  `publicKey` varchar(255) DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK20CEB76EDE6F978E` (`user_id`),
  CONSTRAINT `FK20CEB76EDE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserPreference`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserPreference` (
  `id` bigint(20) NOT NULL,
  `preference` int(11) DEFAULT NULL,
  `value` text DEFAULT NULL,
  `user_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FK9A3C4F26DE6F978E` (`user_id`),
  CONSTRAINT `FK9A3C4F26DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `UserProfile`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `UserProfile` (
  `id` bigint(20) NOT NULL,
  `externalLinkDisplay` varchar(255) DEFAULT NULL,
  `externalLinkURL` varchar(255) DEFAULT NULL,
  `profileText` varchar(2000) DEFAULT NULL,
  `owner_id` bigint(20) NOT NULL,
  `profilePicture_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `owner_id` (`owner_id`),
  KEY `FK3EFA133E974130C9` (`profilePicture_id`),
  CONSTRAINT `FK3EFA133E4A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK3EFA133E974130C9` FOREIGN KEY (`profilePicture_id`) REFERENCES `ImageBlob` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `WhiteListedSysAdminIPAddress`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `WhiteListedSysAdminIPAddress` (
  `id` bigint(20) NOT NULL AUTO_INCREMENT,
  `ipAddress` varchar(255) DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`)
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
  CONSTRAINT `FKB4012D198E86C986` FOREIGN KEY (`signature_id`) REFERENCES `Signature` (`id`),
  CONSTRAINT `FKB4012D19C5434F60` FOREIGN KEY (`witness_id`) REFERENCES `User` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `community_admin`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `community_admin` (
  `community_id` bigint(20) NOT NULL,
  `user_id` bigint(20) NOT NULL,
  PRIMARY KEY (`community_id`,`user_id`),
  KEY `FK12144379DE6F978E` (`user_id`),
  KEY `fk12144379e9e1afe6` (`community_id`),
  CONSTRAINT `FK12144379DE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK12144379E9E1AFE6` FOREIGN KEY (`community_id`) REFERENCES `Community` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `community_labGroups`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `community_labGroups` (
  `community_id` bigint(20) NOT NULL,
  `group_id` bigint(20) NOT NULL,
  PRIMARY KEY (`community_id`,`group_id`),
  KEY `FK46B464EB1CC96626` (`group_id`),
  KEY `fk46b464ebe9e1afe6` (`community_id`),
  CONSTRAINT `FK46B464EB1CC96626` FOREIGN KEY (`group_id`) REFERENCES `rsGroup` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION,
  CONSTRAINT `FK46B464EBE9E1AFE6` FOREIGN KEY (`community_id`) REFERENCES `Community` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecatImageAnnotation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecatImageAnnotation` (
  `id` bigint(20) NOT NULL,
  `annotations` longtext DEFAULT NULL,
  `data` longblob DEFAULT NULL,
  `imageId` bigint(20) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `textAnnotations` varchar(255) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `FKA941D591A94D963` (`record_id`),
  KEY `ecatImageAnnotation_imageId` (`imageId`),
  KEY `ecatImageAnnotation_parentId` (`parentId`),
  CONSTRAINT `FKA941D591A94D963` FOREIGN KEY (`record_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;
DROP TABLE IF EXISTS `ecatImageAnnotation_AUD`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!40101 SET character_set_client = utf8 */;
CREATE TABLE `ecatImageAnnotation_AUD` (
  `id` bigint(20) NOT NULL,
  `REV` int(11) NOT NULL,
  `REVTYPE` tinyint(4) DEFAULT NULL,
  `annotations` longtext DEFAULT NULL,
  `data` longblob DEFAULT NULL,
  `imageId` bigint(20) DEFAULT NULL,
  `parentId` bigint(20) DEFAULT NULL,
  `textAnnotations` varchar(255) DEFAULT NULL,
  `height` int(11) DEFAULT NULL,
  `width` int(11) DEFAULT NULL,
  `record_id` bigint(20) DEFAULT NULL,
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
  `record_id` bigint(20) DEFAULT NULL,
  PRIMARY KEY (`com_id`),
  KEY `FK81AC293A1A94D963` (`record_id`),
  CONSTRAINT `FK81AC293A1A94D963` FOREIGN KEY (`record_id`) REFERENCES `Record` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  `record_id` bigint(20) DEFAULT NULL,
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
  `privateProfile` bit(1) DEFAULT NULL,
  `autoshareEnabled` bit(1) DEFAULT b'0',
  `publicationAllowed` bit(1) DEFAULT b'0',
  `owner_id` bigint(20) DEFAULT NULL,
  `creationDate` datetime(6) DEFAULT NULL,
  `profileText` varchar(255) DEFAULT NULL,
  `communityId` bigint(20) DEFAULT -1,
  `selfService` bit(1) NOT NULL,
  `seoAllowed` bit(1) NOT NULL,
  `enforceOntologies` bit(1) NOT NULL DEFAULT b'0',
  `sharedSnippetGroupFolderId` bigint(20) DEFAULT NULL,
  `allowBioOntologies` bit(1) NOT NULL DEFAULT b'0',
  `permissionStrings` mediumtext DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uniqueName` (`uniqueName`),
  KEY `FK56E1B8BE4A5647A6` (`owner_id`),
  CONSTRAINT `FK56E1B8BE4A5647A6` FOREIGN KEY (`owner_id`) REFERENCES `User` (`id`) ON DELETE NO ACTION ON UPDATE NO ACTION
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
  CONSTRAINT `FK143BF46A3944D3AE` FOREIGN KEY (`role_id`) REFERENCES `roles` (`id`),
  CONSTRAINT `FK143BF46ADE6F978E` FOREIGN KEY (`user_id`) REFERENCES `User` (`id`)
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

