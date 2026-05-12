CREATE TABLE `sys_user` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'Auto-increment primary key',
    `user_id` BIGINT NOT NULL COMMENT 'User unique identifier (business snowflake ID)',
    `username` VARCHAR(50) NOT NULL COMMENT 'Username',
    `email` VARCHAR(100) DEFAULT NULL COMMENT 'User email',
    `password` VARCHAR(255) NOT NULL COMMENT 'Encrypted user password',
    `role` VARCHAR(20) COMMENT 'User permission/role: ADMIN, USER, GUEST',
    `status` TINYINT(1) DEFAULT '1' COMMENT 'Account status: 0-disabled, 1-enabled',
    `is_deleted` TINYINT(1) DEFAULT '0' COMMENT 'Logical delete: 0-not deleted, 1-deleted',
    `last_login_time` DATETIME DEFAULT NULL COMMENT 'Last login time',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT 'Creation time',
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
  PRIMARY KEY (`id`)
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='User login table';

-- Points of Interest table
CREATE TABLE `sys_user_poi` (
                                `id` BIGINT AUTO_INCREMENT COMMENT 'Primary key ID',
                                `user_id` BIGINT NOT NULL COMMENT 'User ID',
                                `poi_tag` VARCHAR(64) NOT NULL COMMENT 'POI tag',
                                `poi_name` VARCHAR(255) NOT NULL COMMENT 'POI name',
                                `poi_address` VARCHAR(500) DEFAULT NULL COMMENT 'POI detailed address',
                                `longitude` DECIMAL(10, 7) NOT NULL COMMENT 'Longitude',
                                `latitude` DECIMAL(10, 7) NOT NULL COMMENT 'Latitude',
                                PRIMARY KEY (`id`),
                                INDEX `idx_user_id` (`user_id`) -- Index on user_id for faster personal favorites lookup
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='User POI (points of interest) table';

CREATE TABLE `sys_ride_order` (
    -- ================= Basic Information =================
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
    `order_id` BIGINT NOT NULL COMMENT 'Order ID',
    `user_id` BIGINT NOT NULL COMMENT 'Passenger user ID',
    `driver_id` BIGINT DEFAULT NULL COMMENT 'Driver ID (filled after accepting order)',
    `mongo_trace_id` VARCHAR(64) DEFAULT NULL COMMENT 'Associated MongoDB trace ID (route planning result)',

    -- ================= Order core parameters =================
    `vehicle_type` INT DEFAULT 1 COMMENT 'Vehicle type (1: Standard, 2: Premium, 3: Luxury)',
    `is_reservation` TINYINT(1) DEFAULT 0 COMMENT 'Is reservation (0:no, 1:yes)',
    `is_expedited` TINYINT(1) DEFAULT 0 COMMENT 'Is expedited (0:no, 1:yes)',
    `safety_code` CHAR(4) DEFAULT NULL COMMENT 'Safety code (used for passenger boarding verification)',

    -- ================= Status transitions =================
    `order_status` TINYINT NOT NULL DEFAULT 10 COMMENT 'Status: 10-created, 20-driver accepted, 30-driver arrived, 40-in transit, 50-completed awaiting payment, 60-paid, 90-cancelled',
    `cancel_role` TINYINT DEFAULT NULL COMMENT 'Cancel role: 1-user, 2-driver, 3-system',
    `cancel_reason` VARCHAR(255) DEFAULT NULL COMMENT 'Cancel reason',

    -- ================= Timeline =================
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Order time',
    `scheduled_time` DATETIME DEFAULT NULL COMMENT 'Scheduled pickup time (reservation only)',
    `driver_accept_time` DATETIME DEFAULT NULL COMMENT 'Driver accept time',
    `driver_arrive_time` DATETIME DEFAULT NULL COMMENT 'Driver arrive start time',
    `pickup_time` DATETIME DEFAULT NULL COMMENT 'Pickup/start trip time (billing starts)',
    `finish_time` DATETIME DEFAULT NULL COMMENT 'Finish/drop-off time (billing ends)',
    `pay_time` DATETIME DEFAULT NULL COMMENT 'Payment time',

    -- ================= Location information =================
    `start_address` VARCHAR(255) NOT NULL COMMENT 'Start structured address text',
    `start_lat` DECIMAL(10, 6) NOT NULL COMMENT 'Start latitude',
    `start_lng` DECIMAL(10, 6) NOT NULL COMMENT 'Start longitude',
    `end_address` VARCHAR(255) NOT NULL COMMENT 'End structured address text',
    `end_lat` DECIMAL(10, 6) NOT NULL COMMENT 'End latitude',
    `end_lng` DECIMAL(10, 6) NOT NULL COMMENT 'End longitude',
    `est_distance` DECIMAL(10, 2) DEFAULT NULL COMMENT 'Estimated distance (Km)',
    `real_distance` DECIMAL(10, 3) DEFAULT NULL COMMENT 'Actual distance (Km)',

    -- ================= Pricing breakdown =================
    `est_price` DECIMAL(10, 2) DEFAULT NULL COMMENT 'Estimated fixed price',
    `real_price` DECIMAL(10, 2) DEFAULT NULL COMMENT 'Actual final fare',
    `price_base` DECIMAL(10, 2) DEFAULT 0.00 COMMENT 'Base distance fee',
    `price_time` DECIMAL(10, 2) DEFAULT 0.00 COMMENT 'Time fee',
    `price_distance` DECIMAL(10, 2) DEFAULT 0.00 COMMENT 'Long distance/empty-run fee',
    `price_expedited` DECIMAL(10, 2) DEFAULT 0.00 COMMENT 'Expedited fee',
    `price_radio` DECIMAL(10, 2) DEFAULT 0.00 COMMENT 'Multiplier ratio',

    -- ================= System fields =================
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'Update time',
    `is_deleted` TINYINT(1) DEFAULT 0 COMMENT 'Logical delete',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_id` (`order_id`),
    KEY `idx_user_create_time` (`user_id`, `create_time`),
    KEY `idx_driver_create_time` (`driver_id`, `create_time`),
    KEY `idx_status_create_time` (`order_status`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ride order table';

create table sys_messages
(
    `id`              bigint auto_increment not null primary key,
    `chat_id` char(36) not null,
    `message_text`    text     not null,
    `created_at`      DATETIME DEFAULT current_timestamp not null
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Conversation history table';
CREATE INDEX idx_chat_id_id_desc ON sys_messages (chat_id, id DESC);

CREATE TABLE `sys_chat_tool_repsonse`
(
    `id`              bigint auto_increment not null primary key,
    `call_id`         varchar(128) not null,
    `chat_id`         char(36) not null,
    `response`    text     not null
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Chat tool response table';

create table `sys_chat`
(
    `id`              bigint auto_increment not null primary key,
    `chat_id`         char(36) not null,
    `user_id`         bigint not null,
    `title`           varchar(255) not null,
    `locked`          tinyint(1) DEFAULT 0 not null,
    `created_at`      DATETIME DEFAULT current_timestamp not null,
    `updated_at`      DATETIME DEFAULT current_timestamp not null
 ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ;

-- Ticket main table
CREATE TABLE `sys_ticket` (
                              `id` bigint NOT NULL AUTO_INCREMENT COMMENT 'Primary key ID',
                              `ticket_id` varchar(16) NOT NULL COMMENT 'External ticket number (business unique identifier)',
                              `user_id` bigint NOT NULL COMMENT 'Initiator ID',
                              `user_type` tinyint(4) NOT NULL DEFAULT 1 COMMENT 'Initiator type: 1-passenger, 2-driver',
                              `order_id` bigint DEFAULT NULL COMMENT 'Related ride order ID',
    -- Core status and classification
                              `ticket_type` tinyint(4) NOT NULL COMMENT 'Ticket type: 1-lost item, 2-fare dispute, 3-service complaint, 4-safety issue, 5-other',
                              `priority` tinyint(4) NOT NULL DEFAULT 1 COMMENT 'Priority: 1-normal, 2-urgent, 3-high',
                              `ticket_status` tinyint(4) NOT NULL DEFAULT 0 COMMENT 'Status: 0-unassigned, 1-in progress, 2-awaiting user confirmation, 3-completed, 4-closed',
    -- Handling process
                              `handler_id` bigint DEFAULT NULL COMMENT 'Current handler (customer service) ID',
    -- Content information
                              `title` varchar(128) NOT NULL COMMENT 'Ticket title',
                              `content` varchar(1536) NOT NULL COMMENT 'Ticket detail description',
    -- Result feedback
                              `process_result` varchar(512) DEFAULT NULL COMMENT 'Processing result summary',
                              `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_ticket_id` (`ticket_id`),
                              KEY `idx_user` (`user_id`, `user_type`),
                              KEY `idx_order_id` (`order_id`),
                              KEY `idx_handler_status` (`handler_id`, `ticket_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ticket records table';

-- Ticket communication records table
CREATE TABLE `sys_ticket_chat` (
                                   `id` bigint NOT NULL AUTO_INCREMENT,
                                   `ticket_id` varchar(16) NOT NULL COMMENT 'Associated sys_ticket ticket ID',
                                   `sender_id` bigint NOT NULL COMMENT 'Sender ID',
                                   `sender_role` tinyint(4) NOT NULL COMMENT 'Sender role: 1-passenger, 2-driver, 3-customer service, 0-system automated',
                                   `content` varchar(1536) NOT NULL COMMENT 'Message content',
                                   `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                   PRIMARY KEY (`id`),
                                   KEY `idx_ticket_id` (`ticket_id`),
                                   KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Ticket communication records table';

CREATE TABLE `sys_qa_info`
(
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY ,
    `group_id` BIGINT NOT NULL ,-- snowflake id
    `answer` TEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Q&A pairs info table';

CREATE TABLE `sys_qa_es`
(
    `id` BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY ,
    `elastic_id` BIGINT NOT NULL , -- snowflake id
    `group_id` BIGINT NOT NULL , -- snowflake id
    `question` VARCHAR(255) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RAG Q&A ES mapping table';