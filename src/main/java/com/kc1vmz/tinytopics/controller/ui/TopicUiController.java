/*
 *
 * TinyTopics
 * Copyright (c) 2026 John Rokicki KC1VMZ
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied
 * warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If not, see
 * https://www.gnu.org/licenses/.
 *
 * http://www.kc1vmz.com
 */
package com.kc1vmz.tinytopics.controller.ui;

import com.kc1vmz.tinytopics.accessor.*;
import com.kc1vmz.tinytopics.generator.GeneratorRegistry;
import com.kc1vmz.tinytopics.model.TinyTopicDefinition;
import com.kc1vmz.tinytopics.model.TinyTopicDefinition.TopicType;
import java.util.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
public class TopicUiController {
    private final TopicAccessor topics;
    private final ResultAccessor results;
    private final GeneratorRegistry generators;
    public TopicUiController(TopicAccessor topics,ResultAccessor results,GeneratorRegistry generators) { this.topics=topics; this.results=results; this.generators=generators; }
    @GetMapping("/") public String list(Model model) { model.addAttribute("topics",topics.list()); return "topics"; }
    @GetMapping("/topics/new") public String create(Model model) { return editor(new TopicForm(),true,model); }
    @GetMapping("/topics/{id}") public String edit(@PathVariable String id,Model model) { return editor(TopicForm.from(topics.get(id)),false,model); }
    @PostMapping("/topics") public String saveNew(@ModelAttribute("form") TopicForm form,Model model) { return save(form,null,model); }
    @PostMapping("/topics/{id}") public String saveExisting(@PathVariable String id,@ModelAttribute("form") TopicForm form,Model model) { return save(form,id,model); }
    @PostMapping("/topics/{id}/delete") public String delete(@PathVariable String id) { topics.delete(id); return "redirect:/"; }
    @GetMapping("/topics/{id}/preview") public String preview(@PathVariable String id,@RequestParam MultiValueMap<String,String> query,Model model) {
        var topic=topics.get(id);
        model.addAttribute("topic",topic);
        model.addAttribute("query",query.toSingleValueMap());
        if(!query.isEmpty()) model.addAttribute("result",results.request(id,query).getBody());
        return "preview";
    }
    private String save(TopicForm form,String id,Model model) {
        try {
            if(id!=null && !id.equals(form.getId())) throw new IllegalArgumentException("Topic IDs cannot be renamed");
            topics.save(form.definition(),id==null);
            return "redirect:/";
        } catch(ResponseStatusException ex) { model.addAttribute("error",ex.getReason()); }
        catch(IllegalArgumentException ex) { model.addAttribute("error",ex.getMessage()); }
        return editor(form,id==null,model);
    }
    private String editor(TopicForm form,boolean create,Model model) {
        model.addAttribute("form",form); model.addAttribute("creating",create); model.addAttribute("generators",generators.names());
        model.addAttribute("generatorParameterNames",generators.parameterNames()); return "editor";
    }
    public static class TopicForm {
        private String id="",description="",parameterNames="",type="text",values="",generatorName="",settings="";
        public TinyTopicDefinition definition() {
            var config=new LinkedHashMap<String,String>();
            for(String line: settings.lines().filter(s -> !s.isBlank()).toList()) {
                int separator=line.indexOf('=');
                if(separator<1) throw new IllegalArgumentException("Settings must use key=value, one per line");
                String key=line.substring(0,separator).trim();
                if(config.putIfAbsent(key,line.substring(separator+1))!=null) throw new IllegalArgumentException("Duplicate setting: "+key);
            }
            return new TinyTopicDefinition(id,description,parameterNames.lines().map(String::trim).filter(s -> !s.isEmpty()).toList(),TopicType.valueOf(type),values.lines().toList(),generatorName,config);
        }
        static TopicForm from(TinyTopicDefinition t) {
            var f=new TopicForm(); f.id=t.id(); f.description=t.description(); f.parameterNames=String.join("\n",t.parameterNames()); f.type=t.type().name(); f.values=String.join("\n",t.values()); f.generatorName=t.generatorName();
            f.settings=String.join("\n",t.generatorSettings().entrySet().stream().sorted(Map.Entry.comparingByKey()).map(e -> e.getKey()+"="+e.getValue()).toList()); return f;
        }
        public String getId(){return id;} public void setId(String v){id=v;}
        public String getDescription(){return description;} public void setDescription(String v){description=v;}
        public String getParameterNames(){return parameterNames;} public void setParameterNames(String v){parameterNames=v;}
        public String getType(){return type;} public void setType(String v){type=v;}
        public String getValues(){return values;} public void setValues(String v){values=v;}
        public String getGeneratorName(){return generatorName;} public void setGeneratorName(String v){generatorName=v;}
        public String getSettings(){return settings;} public void setSettings(String v){settings=v;}
    }
}
