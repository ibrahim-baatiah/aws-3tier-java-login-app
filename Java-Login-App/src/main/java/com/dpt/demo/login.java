package com.dpt.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class login {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Value("${spring.datasource.url}")
	private String url;

	@Value("${spring.datasource.username}")
	private String DBusername;

	@Value("${spring.datasource.password}")
	private String DBpassword;

	@RequestMapping(value = "login", method = RequestMethod.POST)
	public ModelAndView login(String userName, String password) throws ClassNotFoundException {
		Class.forName("com.mysql.cj.jdbc.Driver");

		// Local variables: each request gets its own, nothing leaks between users
		String userId = "";
		String errorMessage = "Invalid username or password";

		String query = "SELECT email, password FROM Employee WHERE username = ?";
		try (Connection con = DriverManager.getConnection(url, DBusername, DBpassword);
				PreparedStatement st = con.prepareStatement(query)) {
			st.setString(1, userName);
			try (ResultSet rs = st.executeQuery()) {
				if (rs.next() && ENCODER.matches(password, rs.getString("password"))) {
					userId = rs.getString("email");
				}
			}
		} catch (SQLException ex) {
			System.out.println("Login DB error: " + ex.getMessage());
			errorMessage = "Login is temporarily unavailable, please try again.";
		}

		ModelAndView mv;
		if (!userId.isEmpty()) {
			mv = new ModelAndView("user");
			mv.addObject("username", userId);
		} else {
			mv = new ModelAndView("login");
			mv.addObject("errorMessage", errorMessage);
		}
		return mv;
	}

	@RequestMapping(value = "login", method = RequestMethod.GET)
	public ModelAndView loginForm() {
		return new ModelAndView("login");
	}
}