package com.example.twitterclone.controller;

import com.example.twitterclone.domain.Message;
import com.example.twitterclone.domain.User;
import com.example.twitterclone.repos.MessageRepository;
import com.example.twitterclone.repos.UserRepo;
import com.mysql.cj.xdevapi.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Controller
public class MainController {

	@Autowired
	UserRepo userRepo;

	@Autowired
	private MessageRepository messageRepository;

	@GetMapping("/")
	public String greeting() {
		return "greeting";
	}

	@Value("${upload.path}")
	private String uploadPath;

	@GetMapping("main")
	public String main(@AuthenticationPrincipal User user, Model model) {
		//Создаем список и передаем туда все сообщения найденные с соответствующей таблицы
		//!(ВАЖНО) Элементы в messages забираются из БД в порядке от старых к новым!!!!!! Дальше это исправим
		Iterable<Message> messages = messageRepository.findAll();

		//Создаем простой список и добавляем туда все элементы из messages
		ArrayList<Message> list = new ArrayList();
		//добавляем все из messages
		messages.forEach(list::add);
		//Разворачиваем наш список, чтобы сперва отображились последние добавленные сообщения (от новых с старым)
		Collections.reverse(list);
		//Передаем список в модель, для отображения на странице
		model.addAttribute("messages", list);
		model.addAttribute("user", user);
		return "main";
	}


	@PostMapping("main")
	public String addMessage(@AuthenticationPrincipal User user,
							 @Valid Message message,
							 BindingResult bindingResult,
							 Model model,
							 @RequestParam("file") MultipartFile file) throws IOException {

		if (bindingResult.hasErrors()) {
			List<FieldError> fieldErrorList = bindingResult.getFieldErrors();
			for (FieldError error : fieldErrorList) {
				System.out.println(error.getField() + "Error" + " " + error.getDefaultMessage());
				model.addAttribute(error.getField() + "Error", error.getDefaultMessage());
				return main(user, model);
			}
		} else {
			message.setAuthor(user);

			//Получаем в форме файл и проверяем существует ли он
			if (file != null && !file.getOriginalFilename().isEmpty()) {
				//Создаем путь до папки, в которую будут сохраняться файлы
				File uploadDir = new File(uploadPath);
				//Если эта папка не существует, то создадим ее
				if (!uploadDir.exists()) {
					uploadDir.mkdir();
				}
				//Обезопасим коллизию и создадим уникальное имя для файла
				String uuidFile = UUID.randomUUID().toString();
				String fileName = uuidFile + "." + file.getOriginalFilename();
				//Перемещаем файл в папку
				file.transferTo(new File(uploadPath + "/" + fileName));
				//Устанавливаем имя файла для объекта message
				message.setFilename(fileName);
			}
			model.addAttribute("message", null);
			messageRepository.save(message);
		}
		Iterable<Message> messages = messageRepository.findAll();
		model.addAttribute("messages", messages);
		return "redirect:/main";

	}

	@PostMapping("filter")
	public String filter(@RequestParam String filter, Model model) {
		List<Message> byTag = messageRepository.findByTag(filter);
		model.addAttribute("messages", byTag);
		return "main";
	}

	@PostMapping("/main/{message}")
	public String deleteMessage(@PathVariable Message message) {
		if (message != null) {
			File file = new File(uploadPath + "/" + message.getFilename());
			if (file.delete()) {
				System.out.println("delete");
			}
			messageRepository.delete(message);
		}
		return "redirect:/main";
	}


	//Контроллер, который выводит информацию об отдельном пользователе
	@GetMapping("/main/user/{user}")
	public String userProfilePage(@PathVariable User user, @AuthenticationPrincipal User currentUser, Model model) {
		model.addAttribute("currentuser", currentUser);
		model.addAttribute("user", user);
		List<Message> byUser = messageRepository.findByAuthor(user);
		Collections.reverse(byUser);
		model.addAttribute("messages", byUser);
		return "user_profile";
	}

	//Контроллер для редактирования пользователя
	@GetMapping("/main/edit/{user}")
	public String selfEdit(@PathVariable User user, Model model) {
		model.addAttribute("user", user);
		return "self_edit";
	}

	@PostMapping("/main/edit/{user}")
	public String saveEditUser(
			@PathVariable User user,
			//Получаем все данные из всех полей в форме
			@RequestParam Map<String, String> form,
			@RequestParam("file") MultipartFile file) throws IOException {

		//Получаем новое имя и статус пользователя
		String newUsername = form.get("username");
		String newStatus = form.get("status");

		//Проверка на существование пользователя
		//ЭТОТ ЖЕ ПРИНЦИП ИСПОЛЬЗУЕТСЯ В ДРУГИХ КОНТРОЛЛЕРАХ, ВЫНЕСТИ В ЮЗЕРСЕРВИС (???)
		User existUser = userRepo.findByUsername(newUsername);
		if (existUser != null && !newUsername.equals(user.getUsername())) {
			return "redirect:/main/edit/" + user.getId();
		}

		if (file != null && !file.getOriginalFilename().isEmpty()) {

			//Если у пользователя уже стоит аватарка, то удаляем старую
			if(user.getIconname() != null){
				File deletable = new File(uploadPath + "/" + user.getIconname());
				if (deletable.delete()) {
					System.out.println("delete");
				}
			}

			//Создаем путь до папки, в которую будут сохраняться файлы
			File uploadDir = new File(uploadPath);
			//Если эта папка не существует, то создадим ее
			if (!uploadDir.exists()) {
				uploadDir.mkdir();
			}
			//Обезопасим коллизию и создадим уникальное имя для файла
			String uuidFile = UUID.randomUUID().toString();
			String fileName = uuidFile + "." + file.getOriginalFilename();
			//Перемещаем файл в папку
			file.transferTo(new File(uploadPath + "/" + fileName));
			//Устанавливаем имя файла для объекта message
			user.setIconname(fileName);
		}
		//Сохраняем отредактированного пользователя
		user.setUsername(newUsername);
		user.setStatus(newStatus);
		userRepo.save(user);
		return "redirect:/main/user/" + user.getId();
	}


}