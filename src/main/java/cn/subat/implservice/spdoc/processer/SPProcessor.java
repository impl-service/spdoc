package cn.subat.implservice.spdoc.processer;

import cn.subat.implservice.spdoc.SPHashMap;
import cn.subat.implservice.spdoc.annotation.*;
import com.google.auto.service.AutoService;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.Tag;

import javax.annotation.Nonnull;
import javax.annotation.processing.*;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes("cn.subat.implservice.spdoc.annotation.*")
@AutoService(Processor.class)
public class SPProcessor extends AbstractProcessor {

    LinkedHashMap<String,Object> docMap;

    public SPProcessor() {
        docMap = new LinkedHashMap<>();
    }

    /**
     * 处理注解
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {

        if(roundEnv.getElementsAnnotatedWith(SPDocDefine.class).size() == 0){
            return false;
        }

        processDefine(roundEnv.getElementsAnnotatedWith(SPDocDefine.class));
        processService(roundEnv.getElementsAnnotatedWith(SPDocService.class));
        processConsumer(roundEnv.getElementsAnnotatedWith(SPDocConsumer.class));

        Yaml yaml = new Yaml();
        try {
            Writer writer = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "spdoc", "api.yaml").openWriter();
            writer.write(yaml.dumpAs(docMap, Tag.MAP, DumperOptions.FlowStyle.BLOCK));
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        InputStream inputStream = getClass().getClassLoader().getResourceAsStream("index.html");
        if (inputStream == null) {
            return true;
        }
        try {
            String html = new String(inputStream.readAllBytes(), "UTF-8");
            Writer writer = processingEnv.getFiler()
                    .createResource(StandardLocation.CLASS_OUTPUT, "spdoc", "index.html").openWriter();
            writer.write(html);
            writer.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return true;
    }

    /**
     * 处理文档定义
     * @param defines 定义集合
     */
    private void processDefine(Set<? extends Element> defines){
        LinkedHashMap<String,String > info = new LinkedHashMap<>();
        docMap.put("openapi","3.0.1");

        for (Element service:defines){
            SPDocDefine docService = service.getAnnotation(SPDocDefine.class);
            String docName = docService.title().isEmpty()?service.getSimpleName().toString() : docService.title();
            String docDescription = docService.description().isEmpty()?"":docService.description();
            String docVersion = docService.version().isEmpty()?"":docService.version();
            String docKey = docService.key().isEmpty()?"":docService.key();
            info.put("key",docKey);
            info.put("title",docName);
            info.put("description",docDescription);
            info.put("version",docVersion);
            docMap.put("components",
                    new SPHashMap("securitySchemes",
                            new SPHashMap("auth",
                                    new SPHashMap(
                                            "type",docService.securitySchema().type(),
                                            "scheme",docService.securitySchema().scheme(),
                                            "bearerFormat",docService.securitySchema().bearerFormat()
                                    )
                            )
                    )
            );
            docMap.put("info",info);
            if(!docService.url().isEmpty()){
                docMap.put("servers",Arrays.asList(new SPHashMap("url",docService.url(),"description","")));
            }
            docMap.put("security",Arrays.asList(new SPHashMap("auth",Arrays.asList())));
        }
    }


    /**
     * 处理所有的服务作为tag
     * @param services 服务集合
     */
    private void processService(Set<? extends Element> services){
        ArrayList<Map<String,String>> list = new ArrayList<>();
        for (Element service:services){
            SPDocService docService = service.getAnnotation(SPDocService.class);
            String serviceName = docService.value().isEmpty()?service.getSimpleName().toString() : docService.value();
            String serviceDescription = docService.description().isEmpty()?"":docService.description();
            LinkedHashMap<String,String> map = new LinkedHashMap<>();
            map.put("name",serviceName);
            map.put("description",serviceDescription);
            list.add(map);
        }
        docMap.put("tags",list);
    }

    private void processConsumer(Set<? extends Element> consumers){
        LinkedHashMap<String,Object> path = new LinkedHashMap<>();
        for (Element consumer:consumers){
            LinkedHashMap<String,Object> map = new LinkedHashMap<>();
            SPDocConsumer docConsumer = consumer.getAnnotation(SPDocConsumer.class);
            String api = "";
            for (AnnotationMirror mirror: consumer.getAnnotationMirrors()){
                Element element = mirror.getAnnotationType().asElement();
                if(element.getSimpleName().toString().equals("Queue")){
                    api = mirror.getElementValues().toString().split("\"")[1];
                }
            }

            if(docConsumer.tag().isEmpty()){
                SPDocService docService = consumer.getEnclosingElement().getAnnotation(SPDocService.class);
                if(docService != null){
                    map.put("tags",new String[]{docService.value()});
                }
            }else{
                map.put("tags",new String[]{docConsumer.tag()});
            }


            map.put("summary",docConsumer.value());
            map.put("description",docConsumer.description());

            LinkedHashMap<String, Object> paramMap = processConsumerParam(consumer);
            if(paramMap.keySet().size()>0){
                map.put(
                        "requestBody", new SPHashMap(
                                "content",new SPHashMap(
                                "application/json",new SPHashMap(
                                "schema",processConsumerParam(consumer)
                        )))
                );
            }

            LinkedHashMap<String,Object> responseMao = new LinkedHashMap<>();
            responseMao.put("1",new SPHashMap(
                            "description","success",
                            "content",new SPHashMap(
                                    "application/json",new SPHashMap(
                                            "schema",processConsumerReturn(consumer)
                                    )
                            )));
            if(docConsumer.code().length > 0){
                for (SPDocCode code:docConsumer.code()){
                    responseMao.put(code.code()+"",new SPHashMap("description",code.msg()));
                }
            }
            map.put("responses",responseMao);
            path.put("/"+api,new SPHashMap("post",map));
        }
        docMap.put("paths",path);
    }


    private LinkedHashMap<String,Object> processConsumerReturn(Element consumer){
        ExecutableElement executable = (ExecutableElement) consumer;
        TypeMirror returnType = executable.getReturnType();
        return typeMirrorToMap(returnType,consumer.asType());
    }


    private LinkedHashMap<String, Object> processConsumerParam(Element consumer){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        ExecutableElement executable = (ExecutableElement) consumer;
        List<? extends VariableElement> parameters = executable.getParameters();


        if(parameters.size() == 0) return map;

        //check the body annotation
        LinkedHashMap<String,Object> subMap = new LinkedHashMap<>();
        ArrayList<String> requiredList = new ArrayList<>();
        for (VariableElement variableElement:parameters){
            boolean required = true;
            String name = camelToSnake(variableElement.getSimpleName().toString());
            for (AnnotationMirror mirror: variableElement.getAnnotationMirrors()){
                Element element = mirror.getAnnotationType().asElement();
                if(element.getSimpleName().toString().equals("Body")){
                    return typeMirrorToMap(variableElement.asType(),consumer.asType());
                }
                if(element.getSimpleName().toString().equals("Nullable")){
                    required = false;
                }
            }
            if(required){
                requiredList.add(name);
            }
            LinkedHashMap<String,Object> map1 = typeMirrorToMap(variableElement.asType(),consumer.asType());
            if(variableElement.getAnnotation(SPDocField.class) != null){
                SPDocField docField = variableElement.getAnnotation(SPDocField.class);
                map1.put("description",docField.value());
                map1.put("format",docField.format());
            }
            subMap.put(name,map1);
        }
        map.put("type","object");
        map.put("required",requiredList);
        map.put("properties",subMap);

        return map;
    }


    private LinkedHashMap<String,Object> declareTypeToMap(DeclaredType declaredType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        for (Element element:declaredType.asElement().getEnclosedElements()){
            if(element.getKind() == ElementKind.FIELD){
                LinkedHashMap<String,Object> childMap = typeMirrorToMap(element.asType(),declaredType);
                if(element.getAnnotation(SPDocField.class) != null){
                    SPDocField docField = element.getAnnotation(SPDocField.class);
                    childMap.put("description",docField.value());
                    childMap.put("format",docField.format());
                }

                String name = element.getSimpleName().toString();
                for (AnnotationMirror mirror: element.getAnnotationMirrors()){
                    Element element1 = mirror.getAnnotationType().asElement();
                    if(element1.getSimpleName().toString().equals("JsonProperty")){
                        name = mirror.getElementValues().toString().split("\"")[1];
                    }
                }
                name = camelToSnake(name);
                map.put(name,childMap);
            }
        }
        return map;
    }

    private LinkedHashMap<String,Object> typeMirrorToMap(TypeMirror parameter,TypeMirror parentType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        switch (parameter.getKind()){
            case DECLARED:{
                DeclaredType parameterType = (DeclaredType) parameter;
                if(parameterType.asElement().getEnclosingElement().toString().equals("java.lang")){
                    map = javaLangTypeToMap(parameterType);
                }else if (parameterType.asElement().getEnclosingElement().toString().equals("java.util")){
                    map = javaUtilTypeToMap(parameterType,parentType);
                }else {
                    map.put("type",parameterType.asElement().getSimpleName().toString());
                    map.put("properties",declareTypeToMap(parameterType));
                    ArrayList<String> requiredList = new ArrayList<>();
                    for (Element element:parameterType.asElement().getEnclosedElements()){
                        if(element.getKind() == ElementKind.FIELD){
                            boolean required = true;
                            String name = camelToSnake(element.getSimpleName().toString());
                            for (AnnotationMirror mirror: element.getAnnotationMirrors()){
                                Element element1 = mirror.getAnnotationType().asElement();
                                if(element1.getSimpleName().toString().equals("Nullable")){
                                    required = false;
                                }
                            }
                            if(required){
                                requiredList.add(name);
                            }
                        }
                    }
                    map.put("required",requiredList);

                }

                break;
            }
            case ARRAY:{
                map.put("type","array");
                map.put("items",typeMirrorToMap(((ArrayType) parameter).getComponentType(),parameter));
                break;
            }
            case INT:{
                map.put("type","integer");
                break;
            }
            case TYPEVAR:{
                if(parentType instanceof DeclaredType){
                    return typeMirrorToMap(((DeclaredType) parentType).getTypeArguments().get(0), parentType);
                }
                break;
            }
            default:{
                map.put("type","string");
            }
        }
        return map;
    }


    private LinkedHashMap<String,Object> javaLangTypeToMap(DeclaredType declaredType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        switch (declaredType.asElement().getSimpleName().toString()){
            case "String":{
                map.put("type","string");
                break;
            }
            case "Integer":
            case "Long":{
                map.put("type","integer");
                break;
            }
            default:{
                //System.out.println(declaredType.asElement().getSimpleName().toString());
            }
        }
        return map;
    }

    private LinkedHashMap<String,Object> javaUtilTypeToMap(DeclaredType declaredType,TypeMirror parentType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        switch (declaredType.asElement().getSimpleName().toString()){
            case "List":{
                map.put("type","array");
                TypeMirror typeMirror = declaredType.getTypeArguments().get(0);
                if(typeMirror instanceof DeclaredType){
                    map.put("items",typeMirrorToMap(typeMirror,typeMirror));
                }else{
                    DeclaredType declaredType1 = (DeclaredType) parentType;
                    if(declaredType1.getTypeArguments().size()>0){
                        map.put("items",typeMirrorToMap(declaredType1.getTypeArguments().get(0),declaredType1.getTypeArguments().get(0)));
                    }
                }
                break;
            }
        }
        return map;
    }


    public static final String CAMEL_CASE_REGEX = "([a-z]+)([A-Z]+)";

    public static final String SNAKE_CASE_PATTERN = "$1\\_$2";


    public String camelToSnake(@Nonnull String str){
        return str.replaceAll(
                        CAMEL_CASE_REGEX,
                        SNAKE_CASE_PATTERN)
                .toLowerCase();
    }



    public static class LowerCamel {
        // function to convert the string into lower camel case
        static String convertString(String s) {
            // to keep track of spaces
            int ctr = 0;
            // variable to hold the length of the string
            int n = s.length();
            // converting the string expression to character array
            char ch[] = s.toCharArray();
            // keep track of indices of ch[ ] array
            int c = 0;
            // traversing through each character of the array
            for (int i = 0; i < n; i++) {
                // The first position of the array i.e., the first letter must be
                // converted to lower case as we are following lower camel case
                // in this program
                if (i == 0)
                    // converting to lower case using the toLowerCase( ) in-built function
                    ch[i] = Character.toLowerCase(ch[i]);
                // as we need to remove all the spaces in between, we check for empty
                // spaces
                if (ch[i] == ' ') {
                    // incrementing the space counter by 1
                    ctr++;
                    // converting the letter immediately after the space to upper case
                    ch[i + 1] = Character.toUpperCase(ch[i + 1]);
                    // continue the loop
                    continue;
                }
                // if the space is not encountered simply copy the character
                else
                    ch[c++] = ch[i];
            }
            // The size of new string will be reduced as the spaces have been removed
            // Thus, returning the new string with new size
            return String.valueOf(ch, 0, n - ctr);

        }
    }
}
