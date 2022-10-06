package com.artion.springboot.app.controllers;

import com.artion.springboot.app.models.entity.Cliente;
import com.artion.springboot.app.models.service.IClienteService;
import com.artion.springboot.app.models.service.IUploadFileService;
import com.artion.springboot.app.util.paginator.PageRender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.support.SessionStatus;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;

@Controller
@SessionAttributes("cliente")
public class ClienteController {

    @Autowired
    private IClienteService clienteService;

    @Autowired
    private IUploadFileService uploadFileService;

    @GetMapping(value = "/uploads/{filename:.+}")
    public ResponseEntity<Resource> verFoto(@PathVariable String filename){

        Resource recurso = null;

        try {
            recurso = uploadFileService.load(filename);
        } catch (MalformedURLException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + recurso.getFilename() + "\"")
                .body(recurso);
    }

    @GetMapping(value = "/ver/{id}")
    public String ver(@PathVariable(value = "id") Long id, Map<String, Object> model, RedirectAttributes flash){
//        Cliente cliente = clienteService.findOne(id);

        Cliente cliente = clienteService.fetchByIdWithFacturas(id);

        if (cliente == null){
            flash.addFlashAttribute("error", "El cliente no existe en la bd");
            return "redirect:/listar";
        }

        model.put("cliente", cliente);
        model.put("titulo", "Detalle cliente: " + cliente.getNombre());

        return "ver";
    }

    @RequestMapping(value={"/listar", "/"}, method = RequestMethod.GET)
    public String listar(@RequestParam(name = "page", defaultValue = "0") int page, Model model){

        Pageable pageRequest = PageRequest.of(page, 4);
        Page<Cliente> clientes = clienteService.findAll(pageRequest);

        PageRender<Cliente> pageRender = new PageRender<>("/listar", clientes);

        model.addAttribute("titulo", "Listado de clientes");
        model.addAttribute("clientes", clientes);
        model.addAttribute("page", pageRender);

        return "listar";
    }

    @RequestMapping(value = "/form")
    public String crear(Map<String, Object> model){
        Cliente cliente = new Cliente();
        model.put("cliente", cliente);
        model.put("titulo", "Formulario de Cliente");
        return "form";
    }

    @RequestMapping(value = "/form/{id}")
    public String editar(@PathVariable(value = "id") Long id, Map<String, Object> model, RedirectAttributes flash) {

        Cliente cliente = null;

        if (id > 0) {
            cliente = clienteService.findOne(id);
            if (cliente == null) {
                flash.addFlashAttribute("error", "El ID del cliente no existe en la BBDD!");
                return "redirect:/listar";
            }
        } else {
            flash.addFlashAttribute("error", "El ID del cliente no puede ser cero!");
            return "redirect:/listar";
        }
        model.put("cliente", cliente);
        model.put("titulo", "Editar Cliente");
        return "form";
    }

    //BindingResult debe estar adyacente a la entity (ex. cliente)
    // el atributo "cliente" del metodo crear se pasa a la vista siempre y cuando el parametro se llame igual "cliente"
    @RequestMapping(value = "/form", method = RequestMethod.POST)
    public String guardar(@Valid Cliente cliente,
                          BindingResult result,
                          Model model,
                          @RequestParam("file") MultipartFile foto,
                          RedirectAttributes flash,
                          SessionStatus status){

        if (result.hasErrors()){
            model.addAttribute("titulo", "Formulario de Cliente");
            return "form";
        }

        //Guarda la imagen adjunta
        if (!foto.isEmpty()){
            if (cliente.getId() != null && cliente.getId() > 0 && cliente.getFoto() != null && cliente.getFoto().length() > 0)
                uploadFileService.delete(cliente.getFoto());


            String uniqueFilename = null;
            try {
                uniqueFilename = uploadFileService.copy(foto);
            } catch (IOException e) {
                e.printStackTrace();
            }

            flash.addFlashAttribute("info", "Ha subido correctamente '" + uniqueFilename + "'");
            cliente.setFoto(uniqueFilename);
        }

        String mensajeFlash = (cliente.getId() != null) ? "Cliente editado con exito!" : "Cliente creado con éxito!";

        clienteService.save(cliente);

        //borra la sesion actual del cliente
        status.setComplete();

        flash.addFlashAttribute("success", mensajeFlash);
        return "redirect:listar";
    }

    @RequestMapping(value = "/eliminar/{id}")
    public String eliminar(@PathVariable(value = "id") Long id, RedirectAttributes flash){

        if (id > 0){
            Cliente cliente = clienteService.findOne(id);

            clienteService.delete(id);
            flash.addFlashAttribute("success", "Cliente eliminado con éxito!");


            if (uploadFileService.delete(cliente.getFoto()))
                flash.addFlashAttribute("info", "Foto " + cliente.getFoto() + " eliminado con éxito!");

        }

        return "redirect:/listar";
    }
}
