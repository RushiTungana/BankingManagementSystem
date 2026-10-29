package com.example.banking;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.regex.Pattern;

public class BankingServer {
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static Connection db() throws SQLException {
        String url = System.getenv().getOrDefault("DB_URL", "jdbc:mysql://localhost:3306/banking_db?sslMode=REQUIRED&serverTimezone=UTC");
        String user = System.getenv().getOrDefault("DB_USER", "root");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "root");
        return DriverManager.getConnection(url, user, password);
    }

    public static void main(String[] args) throws Exception {
        // Static frontend files are served from src/main/resources/web.
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/accounts", BankingServer::accounts);
        server.createContext("/api/deposit", e -> moneyAction(e, true));
        server.createContext("/api/withdraw", e -> moneyAction(e, false));
        server.createContext("/api/balance", BankingServer::balance);
        server.createContext("/api/transactions", BankingServer::transactions);
        server.createContext("/", BankingServer::staticFiles);
        server.setExecutor(null);
        server.start();
        System.out.println("Banking Management System running at http://localhost:" + PORT);
    }

    private static void accounts(HttpExchange e) throws IOException {
        if ("GET".equalsIgnoreCase(e.getRequestMethod())) {
            try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                    "SELECT account_id, full_name, email, account_type, balance, created_at FROM accounts ORDER BY account_id DESC");
                 ResultSet rs = ps.executeQuery()) {
                List<String> rows = new ArrayList<>();
                while (rs.next()) rows.add("{\"id\":" + rs.getLong("account_id") +
                        ",\"name\":" + q(rs.getString("full_name")) + ",\"email\":" + q(rs.getString("email")) +
                        ",\"type\":" + q(rs.getString("account_type")) + ",\"balance\":" + rs.getBigDecimal("balance") +
                        ",\"createdAt\":" + q(rs.getTimestamp("created_at").toString()) + "}");
                send(e, 200, "[" + String.join(",", rows) + "]");
            } catch (SQLException ex) { error(e, 500, "Database error. Check MySQL settings."); }
            return;
        }
        if (!"POST".equalsIgnoreCase(e.getRequestMethod())) { error(e, 405, "Method not allowed."); return; }
        Map<String,String> p = params(e);
        String name = p.getOrDefault("name", "").trim(), email = p.getOrDefault("email", "").trim();
        String type = p.getOrDefault("type", "SAVINGS").trim().toUpperCase(Locale.ROOT);
        if (name.length() < 2 || name.length() > 100 || !EMAIL.matcher(email).matches() ||
                !(type.equals("SAVINGS") || type.equals("CURRENT"))) {
            error(e, 400, "Enter a valid name, email, and account type."); return;
        }
        try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                "INSERT INTO accounts(full_name,email,account_type,balance) VALUES(?,?,?,0.00)", Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name); ps.setString(2, email); ps.setString(3, type); ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) { keys.next(); send(e, 201, "{\"message\":\"Account created successfully.\",\"id\":" + keys.getLong(1) + "}"); }
        } catch (SQLIntegrityConstraintViolationException ex) { error(e, 409, "An account with this email already exists."); }
        catch (SQLException ex) { error(e, 500, "Database error. Check MySQL settings."); }
    }

    private static void moneyAction(HttpExchange e, boolean deposit) throws IOException {
        if (!"POST".equalsIgnoreCase(e.getRequestMethod())) { error(e, 405, "Method not allowed."); return; }
        Map<String,String> p = params(e);
        long id; BigDecimal amount;
        try { id = Long.parseLong(p.getOrDefault("accountId", "")); amount = new BigDecimal(p.getOrDefault("amount", "")); }
        catch (Exception ex) { error(e, 400, "Enter a valid account ID and amount."); return; }
        if (id <= 0 || amount.scale() > 2 || amount.compareTo(BigDecimal.ZERO) <= 0 || amount.precision() > 15) {
            error(e, 400, "Amount must be positive and have no more than two decimal places."); return;
        }
        try (Connection c = db()) {
            c.setAutoCommit(false);
            try {
                BigDecimal current;
                try (PreparedStatement lock = c.prepareStatement("SELECT balance FROM accounts WHERE account_id=? FOR UPDATE")) {
                    lock.setLong(1, id);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) { c.rollback(); error(e, 404, "Account not found."); return; }
                        current = rs.getBigDecimal(1);
                    }
                }
                if (!deposit && current.compareTo(amount) < 0) { c.rollback(); error(e, 400, "Insufficient funds."); return; }
                BigDecimal updated = deposit ? current.add(amount) : current.subtract(amount);
                try (PreparedStatement update = c.prepareStatement("UPDATE accounts SET balance=? WHERE account_id=?")) {
                    update.setBigDecimal(1, updated); update.setLong(2, id); update.executeUpdate();
                }
                try (PreparedStatement tx = c.prepareStatement(
                        "INSERT INTO transactions(account_id,transaction_type,amount,balance_after,description) VALUES(?,?,?,?,?)")) {
                    tx.setLong(1, id); tx.setString(2, deposit ? "DEPOSIT" : "WITHDRAWAL");
                    tx.setBigDecimal(3, amount); tx.setBigDecimal(4, updated);
                    tx.setString(5, deposit ? "Cash deposit" : "Cash withdrawal"); tx.executeUpdate();
                }
                c.commit();
                send(e, 200, "{\"message\":" + q(deposit ? "Deposit completed." : "Withdrawal completed.") +
                        ",\"balance\":" + updated + "}");
            } catch (SQLException ex) { c.rollback(); error(e, 500, "Transaction failed; no changes were saved."); }
            finally { c.setAutoCommit(true); }
        } catch (SQLException ex) { error(e, 500, "Database error. Check MySQL settings."); }
    }

    private static void balance(HttpExchange e) throws IOException {
        Map<String,String> p = queryParams(e);
        try {
            long id = Long.parseLong(p.getOrDefault("accountId", ""));
            try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                    "SELECT account_id,full_name,balance,account_type FROM accounts WHERE account_id=?")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) { error(e, 404, "Account not found."); return; }
                    send(e, 200, "{\"id\":" + rs.getLong(1) + ",\"name\":" + q(rs.getString(2)) +
                            ",\"balance\":" + rs.getBigDecimal(3) + ",\"type\":" + q(rs.getString(4)) + "}");
                }
            }
        } catch (NumberFormatException ex) { error(e, 400, "Provide a valid account ID."); }
        catch (SQLException ex) { error(e, 500, "Database error. Check MySQL settings."); }
    }

    private static void transactions(HttpExchange e) throws IOException {
        Map<String,String> p = queryParams(e);
        try {
            long id = Long.parseLong(p.getOrDefault("accountId", ""));
            try (Connection c = db(); PreparedStatement ps = c.prepareStatement(
                    "SELECT transaction_id,transaction_type,amount,balance_after,description,created_at FROM transactions WHERE account_id=? ORDER BY transaction_id DESC LIMIT 100")) {
                ps.setLong(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    List<String> rows = new ArrayList<>();
                    while (rs.next()) rows.add("{\"id\":" + rs.getLong(1) + ",\"type\":" + q(rs.getString(2)) +
                            ",\"amount\":" + rs.getBigDecimal(3) + ",\"balanceAfter\":" + rs.getBigDecimal(4) +
                            ",\"description\":" + q(rs.getString(5)) + ",\"createdAt\":" + q(rs.getTimestamp(6).toString()) + "}");
                    send(e, 200, "[" + String.join(",", rows) + "]");
                }
            }
        } catch (NumberFormatException ex) { error(e, 400, "Provide a valid account ID."); }
        catch (SQLException ex) { error(e, 500, "Database error. Check MySQL settings."); }
    }

    private static Map<String,String> params(HttpExchange e) throws IOException {
        return decode(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    }
    private static Map<String,String> queryParams(HttpExchange e) {
        String query = e.getRequestURI().getRawQuery();
        return decode(query == null ? "" : query);
    }
    private static Map<String,String> decode(String s) {
        Map<String,String> out = new HashMap<>();
        for (String pair : s.split("&")) {
            if (pair.isEmpty()) continue;
            String[] kv = pair.split("=", 2);
            out.put(URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
                    kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "");
        }
        return out;
    }
    private static String q(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\"";
    }
    private static void error(HttpExchange e, int status, String message) throws IOException {
        send(e, status, "{\"error\":" + q(message) + "}");
    }
    private static void send(HttpExchange e, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        e.getResponseHeaders().set("Cache-Control", "no-store");
        e.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        e.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = e.getResponseBody()) { os.write(bytes); }
    }
    private static void staticFiles(HttpExchange e) throws IOException {
        String path = e.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";
        if (path.contains("..") || path.contains(":")) { error(e, 400, "Invalid path."); return; }
        String resource = "/web" + path;
        try (InputStream in = BankingServer.class.getResourceAsStream(resource)) {
            if (in == null) { byte[] b = "Not found".getBytes(StandardCharsets.UTF_8); e.sendResponseHeaders(404, b.length); try(OutputStream o=e.getResponseBody()){o.write(b);} return; }
            byte[] bytes = in.readAllBytes();
            String type = path.endsWith(".css") ? "text/css; charset=UTF-8" :
                    path.endsWith(".js") ? "text/javascript; charset=UTF-8" : "text/html; charset=UTF-8";
            e.getResponseHeaders().set("Content-Type", type);
            e.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = e.getResponseBody()) { os.write(bytes); }
        }
    }
}
