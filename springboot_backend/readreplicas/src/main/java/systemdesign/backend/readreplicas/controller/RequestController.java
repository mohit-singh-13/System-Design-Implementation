package systemdesign.backend.readreplicas.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import systemdesign.backend.readreplicas.dto.UserDTO;
import systemdesign.backend.readreplicas.model.User;
import systemdesign.backend.readreplicas.service.UserService;

@RestController
@RequestMapping(value = "/user")
public class RequestController {

	@Autowired
	private UserService userService;

	@GetMapping("/{userId}")
	public ResponseEntity<User> getUser(@PathVariable Integer userId) {
		User response = userService.getUser(userId);

		return new ResponseEntity<>(response, HttpStatus.OK);
	}

	@PostMapping("/")
	public ResponseEntity<String> createUser(@RequestBody UserDTO userDto) {
		userService.createUser(userDto);

		return new ResponseEntity<>("User has been created successfully", HttpStatus.OK);
	}

}
