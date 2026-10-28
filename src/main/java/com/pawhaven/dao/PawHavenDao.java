package com.pawhaven.dao;

import com.pawhaven.config.Database;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class PawHavenDao {
    public List<Map<String, Object>> listPets(String search, String species, String breed,
                                               String gender, String location) throws SQLException {
        String sql = "SELECT * FROM pets WHERE "
            + "(? = '' OR LOWER(name) LIKE LOWER(?) OR LOWER(breed) LIKE LOWER(?)) "
                + "AND (? = '' OR species = ?) AND (? = '' OR breed = ?) "
                + "AND (? = '' OR gender = ?) AND (? = '' OR location = ?) ORDER BY id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            int index = 1;
            statement.setString(index++, search);
            statement.setString(index++, "%" + search + "%");
            statement.setString(index++, "%" + search + "%");
            statement.setString(index++, species);
            statement.setString(index++, species);
            statement.setString(index++, breed);
            statement.setString(index++, breed);
            statement.setString(index++, gender);
            statement.setString(index++, gender);
            statement.setString(index++, location);
            statement.setString(index, location);
            try (ResultSet results = statement.executeQuery()) {
                List<Map<String, Object>> pets = new ArrayList<>();
                while (results.next()) {
                    pets.add(readPet(results));
                }
                return pets;
            }
        }
    }

    public Map<String, Object> findPet(int id) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT * FROM pets WHERE id = ?")) {
            statement.setInt(1, id);
            try (ResultSet results = statement.executeQuery()) {
                return results.next() ? readPet(results) : null;
            }
        }
    }

    public int createUser(String name, String email, String phone, String passwordHash,
                          String role) throws SQLException {
        String sql = "INSERT INTO users (name, email, phone, password_hash, role) "
                + "VALUES (?, ?, ?, ?, ?) RETURNING id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, phone);
            statement.setString(4, passwordHash);
            statement.setString(5, role);
            try (ResultSet results = statement.executeQuery()) {
                results.next();
                return results.getInt(1);
            }
        }
    }

    public Map<String, Object> findUserByEmail(String email) throws SQLException {
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT id, name, email, phone, role, password_hash FROM users WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet results = statement.executeQuery()) {
                if (!results.next()) {
                    return null;
                }
                Map<String, Object> user = new LinkedHashMap<>();
                user.put("id", results.getInt("id"));
                user.put("name", results.getString("name"));
                user.put("email", results.getString("email"));
                user.put("phone", results.getString("phone"));
                user.put("role", results.getString("role"));
                user.put("passwordHash", results.getString("password_hash"));
                return user;
            }
        }
    }

    public Map<String, Object> getDashboardData(boolean admin, int userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            Map<String, Object> stats = new LinkedHashMap<>();
            stats.put("pets", count(connection,
                    "SELECT COUNT(*) FROM pets WHERE (? OR shelter_id = ?)", admin, userId));
            stats.put("availablePets", count(connection,
                    "SELECT COUNT(*) FROM pets WHERE status = 'Available' AND (? OR shelter_id = ?)",
                    admin, userId));
            stats.put("adoptions", count(connection,
                    "SELECT COUNT(*) FROM adoption_requests ar LEFT JOIN pets p ON p.id = ar.pet_id "
                            + "WHERE (? OR p.shelter_id = ?)", admin, userId));
            stats.put("rescues", count(connection,
                    "SELECT COUNT(*) FROM rescue_reports WHERE (? OR shelter_id = ?)", admin, userId));
            if (admin) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM users WHERE role <> 'admin'");
                     ResultSet result = statement.executeQuery()) {
                    result.next();
                    stats.put("users", result.getInt(1));
                }
            }

            Map<String, Object> dashboard = new LinkedHashMap<>();
            dashboard.put("stats", stats);
            dashboard.put("adoptions", list(connection,
                    "SELECT ar.id, ar.applicant_name, ar.applicant_email, p.name AS pet_name, "
                            + "COALESCE(s.name, 'Unassigned') AS shelter_name, ar.status, ar.created_at "
                            + "FROM adoption_requests ar LEFT JOIN pets p ON p.id = ar.pet_id "
                            + "LEFT JOIN users s ON s.id = p.shelter_id "
                            + "WHERE (? OR p.shelter_id = ?) ORDER BY ar.created_at DESC LIMIT 50",
                    admin, userId));
            dashboard.put("rescueReports", list(connection,
                    "SELECT id, animal_type, location, emergency_level, animal_condition, shelter_id, "
                            + "reporter_name, reporter_phone, status, created_at "
                            + "FROM rescue_reports WHERE (? OR shelter_id = ?) "
                            + "ORDER BY created_at DESC LIMIT 50", admin, userId));
            dashboard.put("pets", list(connection,
                    "SELECT id, name, species, breed, age, gender, location, status, shelter_id "
                            + "FROM pets WHERE (? OR shelter_id = ?) ORDER BY id DESC LIMIT 100",
                    admin, userId));
            if (admin) {
                dashboard.put("shelters", listShelters(connection));
                dashboard.put("contactMessages", list(connection,
                    "SELECT id, name, email, subject, message, created_at "
                        + "FROM contact_messages WHERE (? OR id = ?) "
                        + "ORDER BY created_at DESC LIMIT 50", true, userId));
            }
            return dashboard;
        }
    }

            public Map<String, Object> getUserDashboardData(int userId) throws SQLException {
        try (Connection connection = Database.getConnection()) {
            Map<String, Object> stats = new LinkedHashMap<>();
                stats.put("applications", countByUser(connection,
                    "SELECT COUNT(*) FROM adoption_requests WHERE applicant_user_id = ?", userId));
                stats.put("adopted", countByUser(connection,
                    "SELECT COUNT(*) FROM adoption_requests WHERE status = 'Approved' "
                        + "AND applicant_user_id = ?", userId));
                stats.put("rescueReports", countByUser(connection,
                    "SELECT COUNT(*) FROM rescue_reports WHERE reporter_user_id = ?", userId));
                    try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT COUNT(*) FROM pets WHERE status = 'Available'");
                     ResultSet result = statement.executeQuery()) {
                    result.next();
                    stats.put("availablePets", result.getInt(1));
                    }
            Map<String, Object> dashboard = new LinkedHashMap<>();
            dashboard.put("stats", stats);
                dashboard.put("adoptions", listByUser(connection,
                    "SELECT ar.id, ar.applicant_name, p.name AS pet_name, ar.status, ar.created_at "
                            + "FROM adoption_requests ar LEFT JOIN pets p ON p.id = ar.pet_id "
                        + "WHERE ar.applicant_user_id = ? "
                        + "ORDER BY ar.created_at DESC LIMIT 50", userId));
            dashboard.put("rescueReports", listByUser(connection,
                    "SELECT id, animal_type, location, emergency_level, status, created_at "
                            + "FROM rescue_reports WHERE reporter_user_id = ? "
                            + "ORDER BY created_at DESC LIMIT 50", userId));
            return dashboard;
        }
    }

    public int createPet(int shelterId, String name, String species, String breed, int age,
                         String gender, String location, String health, boolean vaccinated,
                         boolean dewormed, String image, String description) throws SQLException {
        String sql = "INSERT INTO pets (shelter_id, name, species, breed, age, gender, location, "
                + "health, vaccinated, dewormed, image, description) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, shelterId);
            statement.setString(2, name);
            statement.setString(3, species);
            statement.setString(4, breed);
            statement.setInt(5, age);
            statement.setString(6, gender);
            statement.setString(7, location);
            statement.setString(8, health);
            statement.setBoolean(9, vaccinated);
            statement.setBoolean(10, dewormed);
            statement.setString(11, image);
            statement.setString(12, description);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    public boolean assignPetToShelter(int petId, int shelterId) throws SQLException {
        String sql = "UPDATE pets p SET shelter_id = s.id FROM users s "
                + "WHERE p.id = ? AND s.id = ? AND s.role = 'shelter'";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, petId);
            statement.setInt(2, shelterId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateAdoptionStatus(int requestId, String status, boolean admin, int userId)
            throws SQLException {
        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Integer petId = null;
                String previousStatus;
                String lookup = "SELECT ar.pet_id, ar.status FROM adoption_requests ar "
                        + "LEFT JOIN pets p ON p.id = ar.pet_id "
                        + "WHERE ar.id = ? AND (? OR p.shelter_id = ?) FOR UPDATE OF ar";
                try (PreparedStatement statement = connection.prepareStatement(lookup)) {
                    statement.setInt(1, requestId);
                    statement.setBoolean(2, admin);
                    statement.setInt(3, userId);
                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            connection.rollback();
                            return false;
                        }
                        int foundPetId = result.getInt("pet_id");
                        if (!result.wasNull()) {
                            petId = foundPetId;
                        }
                        previousStatus = result.getString("status");
                    }
                }

                if ("Approved".equals(status) && petId != null) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT status FROM pets WHERE id = ? FOR UPDATE")) {
                        statement.setInt(1, petId);
                        try (ResultSet result = statement.executeQuery()) {
                            if (!result.next() || (!"Available".equals(result.getString(1))
                                    && !"Approved".equals(previousStatus))) {
                                connection.rollback();
                                return false;
                            }
                        }
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM adoption_requests WHERE pet_id = ? "
                                    + "AND status = 'Approved' AND id <> ?")) {
                        statement.setInt(1, petId);
                        statement.setInt(2, requestId);
                        try (ResultSet result = statement.executeQuery()) {
                            result.next();
                            if (result.getInt(1) > 0) {
                                connection.rollback();
                                return false;
                            }
                        }
                    }
                }

                try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE adoption_requests SET status = ? WHERE id = ?")) {
                    statement.setString(1, status);
                    statement.setInt(2, requestId);
                    statement.executeUpdate();
                }
                if (petId != null && "Approved".equals(status)) {
                    updatePetStatus(connection, petId, "Adopted");
                } else if (petId != null && "Approved".equals(previousStatus)) {
                    try (PreparedStatement statement = connection.prepareStatement(
                            "SELECT COUNT(*) FROM adoption_requests WHERE pet_id = ? AND status = 'Approved'")) {
                        statement.setInt(1, petId);
                        try (ResultSet result = statement.executeQuery()) {
                            result.next();
                            if (result.getInt(1) == 0) {
                                updatePetStatus(connection, petId, "Available");
                            }
                        }
                    }
                }
                connection.commit();
                return true;
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    public boolean assignRescueToShelter(int reportId, int shelterId) throws SQLException {
        String sql = "UPDATE rescue_reports r SET shelter_id = s.id "
                + "FROM users s WHERE r.id = ? AND s.id = ? AND s.role = 'shelter'";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, reportId);
            statement.setInt(2, shelterId);
            return statement.executeUpdate() == 1;
        }
    }

    public boolean updateRescueStatus(int reportId, String status, boolean admin, int userId)
            throws SQLException {
        String sql = "UPDATE rescue_reports SET status = ? WHERE id = ? AND (? OR shelter_id = ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, reportId);
            statement.setBoolean(3, admin);
            statement.setInt(4, userId);
            return statement.executeUpdate() == 1;
        }
    }

    private void updatePetStatus(Connection connection, int petId, String status) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE pets SET status = ? WHERE id = ?")) {
            statement.setString(1, status);
            statement.setInt(2, petId);
            statement.executeUpdate();
        }
    }

    private List<Map<String, Object>> listShelters(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id, name, email FROM users WHERE role = 'shelter' ORDER BY name");
             ResultSet results = statement.executeQuery()) {
            List<Map<String, Object>> shelters = new ArrayList<>();
            while (results.next()) {
                Map<String, Object> shelter = new LinkedHashMap<>();
                shelter.put("id", results.getInt("id"));
                shelter.put("name", results.getString("name"));
                shelter.put("email", results.getString("email"));
                shelters.add(shelter);
            }
            return shelters;
        }
    }

    private int countByUser(Connection connection, String sql, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private List<Map<String, Object>> listByUser(Connection connection, String sql, int userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userId);
            return readRows(statement);
        }
    }

    private List<Map<String, Object>> readRows(PreparedStatement statement) throws SQLException {
        try (ResultSet results = statement.executeQuery()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            int columnCount = results.getMetaData().getColumnCount();
            while (results.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int column = 1; column <= columnCount; column++) {
                    String label = results.getMetaData().getColumnLabel(column);
                    Object value = "created_at".equals(label)
                            ? results.getTimestamp(column).toInstant().toString()
                            : results.getObject(column);
                    row.put(label, value);
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private int count(Connection connection, String sql, boolean admin, int userId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, admin);
            statement.setInt(2, userId);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }

    private List<Map<String, Object>> list(Connection connection, String sql,
                                            boolean admin, int userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setBoolean(1, admin);
            statement.setInt(2, userId);
            return readRows(statement);
        }
    }

    public int createAdoptionRequest(Integer petId, Integer applicantUserId,
                                     String name, String email, String phone,
                                     String preferredDate, String housingType,
                                     String otherPets, String children, String reason)
            throws SQLException {
        String sql = "INSERT INTO adoption_requests (pet_id, applicant_user_id, applicant_name, "
                + "applicant_email, phone, preferred_date, housing_type, has_other_pets, has_children, reason) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (petId == null) {
                statement.setNull(1, java.sql.Types.INTEGER);
            } else {
                statement.setInt(1, petId);
            }
            if (applicantUserId == null) {
                statement.setNull(2, java.sql.Types.INTEGER);
            } else {
                statement.setInt(2, applicantUserId);
            }
            statement.setString(3, name);
            statement.setString(4, email);
            statement.setString(5, phone);
            if (preferredDate.isBlank()) {
                statement.setNull(6, java.sql.Types.DATE);
            } else {
                statement.setDate(6, Date.valueOf(preferredDate));
            }
            statement.setString(7, housingType);
            statement.setString(8, otherPets);
            statement.setString(9, children);
            statement.setString(10, reason);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public int createRescueReport(String animalType, String location, String emergencyLevel,
                                  String condition, String description, String reporterName,
                                  String reporterPhone, Integer reporterUserId, String imageUrl)
            throws SQLException {
        String sql = "INSERT INTO rescue_reports (animal_type, location, emergency_level, "
                + "animal_condition, description, reporter_name, reporter_phone, reporter_user_id, image_url) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, animalType);
            statement.setString(2, location);
            statement.setString(3, emergencyLevel);
            statement.setString(4, condition);
            statement.setString(5, description);
            statement.setString(6, reporterName);
            statement.setString(7, reporterPhone);
            if (reporterUserId == null) {
                statement.setNull(8, java.sql.Types.INTEGER);
            } else {
                statement.setInt(8, reporterUserId);
            }
            statement.setString(9, imageUrl);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    public int createContactMessage(String name, String email, String subject, String message)
            throws SQLException {
        String sql = "INSERT INTO contact_messages (name, email, subject, message) VALUES (?, ?, ?, ?)";
        try (Connection connection = Database.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setString(1, name);
            statement.setString(2, email);
            statement.setString(3, subject);
            statement.setString(4, message);
            statement.executeUpdate();
            try (ResultSet keys = statement.getGeneratedKeys()) {
                keys.next();
                return keys.getInt(1);
            }
        }
    }

    private Map<String, Object> readPet(ResultSet results) throws SQLException {
        Map<String, Object> pet = new LinkedHashMap<>();
        pet.put("id", results.getInt("id"));
        pet.put("name", results.getString("name"));
        pet.put("species", results.getString("species"));
        pet.put("breed", results.getString("breed"));
        pet.put("age", results.getInt("age"));
        pet.put("gender", results.getString("gender"));
        pet.put("location", results.getString("location"));
        pet.put("health", results.getString("health"));
        pet.put("vaccinated", results.getBoolean("vaccinated"));
        pet.put("dewormed", results.getBoolean("dewormed"));
        pet.put("status", results.getString("status"));
        pet.put("image", results.getString("image"));
        pet.put("description", results.getString("description"));
        return pet;
    }
}