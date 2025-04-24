<?php

require_once 'config.php';
require_once 'email_config.php';

header('Content-Type: application/json');

$mailbox = "{" . $email_server . ":" . $email_port . "/imap/ssl}". $email_folder;

error_reporting(E_ALL);
ini_set('display_errors', 1);

$response = [
    'status' => 'error',
    'emails_count' => 0,
    'data' => null
];

$api_key = isset($_REQUEST['api_key']) ? $_REQUEST['api_key'] : '';
$is_authenticated = false;

if (empty($api_key)) {
    $response['status'] = 'error';
    echo json_encode($response);
    exit;
} else {
    try {
        $conn = new mysqli($servername, $username, $password, "Valid_Api_Keys");
        
        if ($conn->connect_error) {
            throw new Exception("Database connection failed: " . $conn->connect_error);
        }
        
        $stmt = $conn->prepare("SELECT permissions, expires_at FROM api_keys WHERE `key` = ?");
        if (!$stmt) {
            throw new Exception("Database prepare failed: " . $conn->error);
        }
        
        $stmt->bind_param("s", $api_key);
        $stmt->execute();
        $stmt->store_result();
        
        if ($stmt->num_rows > 0) {
            $stmt->bind_result($permissions, $expires_at);
            $stmt->fetch();
            
            $current_date = date('Y-m-d H:i:s');
            $is_valid = ($permissions == 1) && ($expires_at === null || $expires_at > $current_date);
            
            if ($is_valid) {
                $is_authenticated = true;
            } else {
                $response['status'] = 'error';
                echo json_encode($response);
                exit;
            }
        } else {
            $response['status'] = 'error';
            echo json_encode($response);
            exit;
        }
        
        $stmt->close();
        $conn->close();
        
    } catch (Exception $e) {
        $response['status'] = 'error';
        echo json_encode($response);
        exit;
    }
}

if ($is_authenticated) {
    try {
        $inbox = imap_open($mailbox, $email_account, $email_password);
        
        if (!$inbox) {
            throw new Exception('Cannot connect to mail server: ' . imap_last_error());
        }
        
        $emails = imap_search($inbox, 'ALL');
        
        $email_data = [];
        $processed_emails = [];
        
        $conn = new mysqli($servername, $username, $password, "email_db");
        if ($conn->connect_error) {
            throw new Exception("Database connection failed: " . $conn->connect_error);
        }
        
        $create_table_sql = "CREATE TABLE IF NOT EXISTS processed_emails (
            id INT AUTO_INCREMENT PRIMARY KEY,
            email_id VARCHAR(255) NOT NULL,
            mailbox VARCHAR(255) NOT NULL,
            processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            UNIQUE KEY (email_id, mailbox)
        )";
        
        if (!$conn->query($create_table_sql)) {
            throw new Exception("Failed to create table: " . $conn->error);
        }
        
        $stmt = $conn->prepare("SELECT email_id FROM processed_emails WHERE mailbox = ?");
        if (!$stmt) {
            throw new Exception("Database prepare failed: " . $conn->error);
        }
        
        $stmt->bind_param("s", $mailbox);
        $stmt->execute();
        $result = $stmt->get_result();
        
        $processed_email_ids = [];
        while ($row = $result->fetch_assoc()) {
            $processed_email_ids[] = $row['email_id'];
        }
        
        $stmt->close();
        
        if ($emails) {
            rsort($emails);
            $new_email_ids = [];
            
            foreach($emails as $email_number) {
                if (in_array($email_number, $processed_email_ids)) {
                    continue;
                }
                
                $header = imap_headerinfo($inbox, $email_number);
                $from = $header->from[0]->mailbox . "@" . $header->from[0]->host;
                $subject = isset($header->subject) ? $header->subject : '[No Subject]';
                $date = date('Y-m-d H:i:s', strtotime($header->date));
                
                $body = imap_fetchbody($inbox, $email_number, 1);
                if (empty(trim($body))) {
                    $body = imap_fetchbody($inbox, $email_number, 2);
                }
                
                $structure = imap_fetchstructure($inbox, $email_number);
                if (isset($structure->encoding) && $structure->encoding == 4) {
                    $body = quoted_printable_decode($body);
                } elseif (isset($structure->encoding) && $structure->encoding == 3) {
                    $body = base64_decode($body);
                }
                
                $thread_id = null;
                $message_id = null;
                
                $overview = imap_fetch_overview($inbox, $email_number, 0);
                if (!empty($overview[0]->message_id)) {
                    $message_id = trim($overview[0]->message_id);
                }
                
                $headers = imap_fetchheader($inbox, $email_number);
                if (preg_match('/In-Reply-To: <([^>]+)>/i', $headers, $matches)) {
                    $thread_id = $matches[1];
                }
                elseif (preg_match('/References: (.+)/i', $headers, $matches)) {
                    $refs = explode(' ', $matches[1]);
                    if (!empty($refs)) {
                        $thread_id = trim(str_replace(['<', '>'], '', $refs[0]));
                    }
                }
                
                if (empty($thread_id) && !empty($message_id)) {
                    $thread_id = $message_id;
                }
                
                $table_name = "thread_" . preg_replace('/[^a-zA-Z0-9_]/', '_', $from);
                
                $table_check_query = "SHOW TABLES LIKE '$table_name'";
                $table_exists = $conn->query($table_check_query)->num_rows > 0;
                
                if (!$table_exists) {
                    $create_email_table_sql = "CREATE TABLE `$table_name` (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        email_id VARCHAR(255) NOT NULL,
                        date DATETIME NOT NULL,
                        sender VARCHAR(255) NOT NULL,
                        subject TEXT,
                        body LONGTEXT,
                        message_id VARCHAR(255),
                        thread_id VARCHAR(255),
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        INDEX (thread_id)
                    )";
                    
                    if (!$conn->query($create_email_table_sql)) {
                        throw new Exception("Failed to create email table: " . $conn->error);
                    }
                } else {
                    $column_check_query = "SHOW COLUMNS FROM `$table_name` LIKE 'thread_id'";
                    $column_exists = $conn->query($column_check_query)->num_rows > 0;
                    
                    if (!$column_exists) {
                        $alter_table_sql = "ALTER TABLE `$table_name` 
                            ADD COLUMN message_id VARCHAR(255) AFTER body,
                            ADD COLUMN thread_id VARCHAR(255) AFTER message_id,
                            ADD INDEX (thread_id)";
                        if (!$conn->query($alter_table_sql)) {
                            throw new Exception("Failed to alter table: " . $conn->error);
                        }
                    }
                }
                
                $insert_stmt = $conn->prepare("INSERT INTO `$table_name` (email_id, date, sender, subject, body, message_id, thread_id) VALUES (?, ?, ?, ?, ?, ?, ?)");
                if (!$insert_stmt) {
                    throw new Exception("Database prepare failed: " . $conn->error);
                }
                
                $insert_stmt->bind_param("sssssss", $email_number, $date, $from, $subject, $body, $message_id, $thread_id);
                $insert_stmt->execute();
                $insert_stmt->close();
                
                $email_data[] = [
                    'id' => $email_number,
                    'date' => $date,
                    'from' => $from,
                    'subject' => $subject,
                    'body' => $body,
                    'message_id' => $message_id,
                    'thread_id' => $thread_id
                ];
                
                $new_email_ids[] = $email_number;
            }
            
            if (!empty($new_email_ids)) {
                $insert_stmt = $conn->prepare("INSERT IGNORE INTO processed_emails (email_id, mailbox) VALUES (?, ?)");
                if (!$insert_stmt) {
                    throw new Exception("Database prepare failed: " . $conn->error);
                }
                
                foreach ($new_email_ids as $email_id) {
                    $insert_stmt->bind_param("ss", $email_id, $mailbox);
                    $insert_stmt->execute();
                }
                
                $insert_stmt->close();
            }
            
            $response['status'] = 'success';
            $response['emails_count'] = count($email_data);
            $response['data'] = $email_data;
            
        } else {
            $response['status'] = 'success';
            $response['emails_count'] = 0;
            $response['data'] = [];
        }
        
        $conn->close();
        imap_close($inbox);
        
    } catch (Exception $e) {
        $response['status'] = 'error';
        $response['message'] = $e->getMessage();
        if (isset($inbox) && $inbox) {
            imap_close($inbox);
        }
        if (isset($conn) && $conn) {
            $conn->close();
        }
    }
} else {
    $response['status'] = 'error';
}

echo json_encode($response);
?>
