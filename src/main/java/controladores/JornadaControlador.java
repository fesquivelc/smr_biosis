/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package controladores;

import entidades.Jornada;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author fesquivelc
 */
public class JornadaControlador extends Controlador<Jornada>{
    
    private JornadaControlador() {
        super(Jornada.class);
    }
    
    public List<Jornada> buscarXTipo(char tipo){
        String jpql = "SELECT j FROM Jornada j WHERE j.tipo = :tipo";
        Map<String, Object> map = new HashMap();
        map.put("tipo", tipo);
        return this.getDao().buscar(jpql, map);
    }
    
    public static JornadaControlador getInstance() {
        return JornadaControladorHolder.INSTANCE;
    }
    
    private static class JornadaControladorHolder {

        private static final JornadaControlador INSTANCE = new JornadaControlador();
    }
}
