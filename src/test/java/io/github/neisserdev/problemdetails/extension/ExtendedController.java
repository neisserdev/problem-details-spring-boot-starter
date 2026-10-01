package io.github.neisserdev.problemdetails.extension;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.neisserdev.problemdetails.ResourceNotFoundException;

@RestController
@RequestMapping("/extension")
class ExtendedController {

    @GetMapping("/items/{id}")
    String item(@PathVariable("id") long id) {
        throw new ResourceNotFoundException("Item", id);
    }

    @GetMapping("/retired")
    String retired() {
        throw new ExtendedExceptionHandler.ItemRetiredException();
    }

    @GetMapping("/failure")
    String failure() {
        throw new IllegalStateException("unexpected failure");
    }
}
