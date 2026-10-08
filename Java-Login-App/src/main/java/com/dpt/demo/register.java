package com.dpt.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class register {

	private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

	@Value("${spring.datasource.url}")
	private String url;

	@Value("${spring.datasource.username}")
	private String DBusername;

	@Value("${spring.datasource.password}")
	private String DBpassword;

	@RequestMapping(value = "register", method = RequestMethod.GET)
	public ModelAndView registerform() {
		return new ModelAndView("register");
	}

	@RequestMapping(value = "register", method = RequestMethod.POST)
	public ModelAndView register(String firstName, String lastName, String email,
			String userName, String password) throws ClassNotFoundException {
		Class.forName("com.mysql.cj.jdbc.Driver");

		String message;
		String sql = "INSERT INTO Employee (first_name, last_name, email, username, password, regdate) "
				+ "VALUES (?, ?, ?, ?, ?, CURDATE())";
		try (Connection con = DriverManager.getConnection(url, DBusername, DBpassword);
				PreparedStatement st = con.prepareStatement(sql)) {
			st.setString(1, firstName);
			st.setString(2, lastName);
			st.setString(3, email);
			st.setString(4, userName);
			st.setString(5, ENCODER.encode(password));
			st.executeUpdate();
			message = "user account has been added for " + userName;
		} catch (SQLException ex) {
			System.out.println("Register DB error: " + ex.getMessage());
			message = "Registration failed, please try again.";
		}

		ModelAndView mv = new ModelAndView("register");
		mv.addObject("message", message);
		return mv;
	}
}