package com.example;

import com.example.controller.AppController;
import com.example.model.AppModel;

public class App {
    public static void main(String[] args) {
        AppModel model = new AppModel();
        AppController controller = new AppController(model);
        controller.iniciarAplicacion();
    }
}