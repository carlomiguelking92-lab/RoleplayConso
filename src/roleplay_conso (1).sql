-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Aug 31, 2026 at 12:13 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.2.12

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `roleplay_conso`
--

-- --------------------------------------------------------

--
-- Table structure for table `activity_logs`
--

CREATE TABLE `activity_logs` (
  `id` int(11) NOT NULL,
  `action_type` varchar(255) NOT NULL,
  `user_details` varchar(255) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `certificates`
--

CREATE TABLE `certificates` (
  `id` int(11) NOT NULL,
  `ref_code` varchar(20) NOT NULL,
  `personnel_name` varchar(250) NOT NULL,
  `id_or_badge` varchar(50) NOT NULL,
  `template_type` varchar(100) NOT NULL,
  `issued_date` varchar(50) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `certificates`
--

INSERT INTO `certificates` (`id`, `ref_code`, `personnel_name`, `id_or_badge`, `template_type`, `issued_date`, `created_at`) VALUES
(19, 'REF-1BF6E113', 'Police General Carlo', 'O09-01002', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:54:51'),
(20, 'REF-7461C1EC', 'Police Colonel Kyler', 'O09-01003', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:54:54'),
(21, 'REF-E1F7DA8D', 'Police Lieutenant General Liloly', 'O09-01001', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:54:59'),
(22, 'REF-6DB3997E', 'Police Lieutenant Potato', 'O09-01006', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:03'),
(23, 'REF-085C4D78', 'Patrolman Tobias', '09-01011', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:07'),
(24, 'REF-850035C5', 'Patrolman Xin', '09-01008', 'PNP - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:11'),
(25, 'REF-A8C3B2EB', 'Governor Aljade', 'GO-0001', 'Gov Office - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:26'),
(26, 'REF-235CB78B', 'Mayor Kyy', 'GO-0002', 'Gov Office - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:31'),
(27, 'REF-684F562F', 'Human Resources Kzh', 'GO-0005', 'Gov Office - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:34'),
(28, 'REF-BB9C94C4', 'Personal Assistant to the Mayor Matcha', 'GO-0003', 'Gov Office - Certificate of Appreciation', 'August 30, 2026', '2026-08-30 05:55:38');

-- --------------------------------------------------------

--
-- Table structure for table `government_members`
--

CREATE TABLE `government_members` (
  `name` varchar(100) NOT NULL,
  `position` varchar(100) NOT NULL,
  `id_no` varchar(50) NOT NULL,
  `points` int(11) DEFAULT 0,
  `discord_id` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `government_members`
--

INSERT INTO `government_members` (`name`, `position`, `id_no`, `points`, `discord_id`) VALUES
('Aljade', 'Governor', 'GO-0001', 0, NULL),
('Kyy', 'Mayor', 'GO-0002', 0, NULL),
('Matcha', 'Personal Assistant to the Mayor', 'GO-0003', 0, NULL),
('Kirk', 'Personal Assistan to the Governor', 'GO-0004', 0, NULL),
('Creutzfeldt-Jakob', 'Regional Health Officer', 'GO-0005', 0, NULL),
('Kzh', 'Human Resources', 'GO-0006', 0, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `officers`
--

CREATE TABLE `officers` (
  `name` varchar(100) NOT NULL,
  `rank` varchar(100) NOT NULL,
  `badge_no` varchar(100) NOT NULL,
  `points` int(11) DEFAULT 0,
  `discord_id` varchar(50) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `officers`
--

INSERT INTO `officers` (`name`, `rank`, `badge_no`, `points`, `discord_id`) VALUES
('Cess', 'Police Executive Master Seargent', '09-01005', 0, NULL),
('Xin', 'Patrolman', '09-01008', 0, NULL),
('Kai', 'Patrolman', '09-01009', 0, NULL),
('Watataps', 'Patrolman', '09-01010', 0, NULL),
('Tobias', 'Patrolman', '09-01011', 0, NULL),
('Liloly', 'Police Lieutenant General', 'O09-01001', 0, NULL),
('Carlo', 'Police General', 'O09-01002', 0, '472624267415257089'),
('Kyler', 'Police Colonel', 'O09-01003', 0, NULL),
('Ayasib', 'Police Major', 'O09-01004', 0, NULL),
('Potato', 'Police Lieutenant', 'O09-01006', 0, NULL);

-- --------------------------------------------------------

--
-- Table structure for table `proof_logs`
--

CREATE TABLE `proof_logs` (
  `id` int(11) NOT NULL,
  `badge_no` varchar(20) DEFAULT NULL,
  `criteria_type` varchar(50) DEFAULT NULL,
  `points_awarded` int(11) DEFAULT NULL,
  `proof_url` text DEFAULT NULL,
  `timestamp` datetime DEFAULT current_timestamp()
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Indexes for dumped tables
--

--
-- Indexes for table `activity_logs`
--
ALTER TABLE `activity_logs`
  ADD PRIMARY KEY (`id`);

--
-- Indexes for table `certificates`
--
ALTER TABLE `certificates`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `ref_code` (`ref_code`);

--
-- Indexes for table `government_members`
--
ALTER TABLE `government_members`
  ADD PRIMARY KEY (`id_no`),
  ADD UNIQUE KEY `discord_id` (`discord_id`);

--
-- Indexes for table `officers`
--
ALTER TABLE `officers`
  ADD PRIMARY KEY (`badge_no`),
  ADD UNIQUE KEY `discord_id` (`discord_id`);

--
-- Indexes for table `proof_logs`
--
ALTER TABLE `proof_logs`
  ADD PRIMARY KEY (`id`);

--
-- AUTO_INCREMENT for dumped tables
--

--
-- AUTO_INCREMENT for table `activity_logs`
--
ALTER TABLE `activity_logs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT for table `certificates`
--
ALTER TABLE `certificates`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=29;

--
-- AUTO_INCREMENT for table `proof_logs`
--
ALTER TABLE `proof_logs`
  MODIFY `id` int(11) NOT NULL AUTO_INCREMENT;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
