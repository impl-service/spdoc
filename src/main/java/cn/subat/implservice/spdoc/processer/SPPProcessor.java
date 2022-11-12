package cn.subat.implservice.spdoc.processer;

import cn.subat.implservice.spdoc.annotation.SPDoc;
import cn.subat.implservice.spdoc.annotation.SPDocConsumer;
import cn.subat.implservice.spdoc.annotation.SPDocDefine;
import cn.subat.implservice.spdoc.annotation.SPDocService;
import com.google.auto.service.AutoService;
import org.yaml.snakeyaml.Yaml;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.*;
import javax.lang.model.type.*;
import javax.tools.StandardLocation;
import java.io.*;
import java.util.*;

@SupportedAnnotationTypes("cn.subat.implservice.spdoc.annotation.*")
@SupportedSourceVersion(SourceVersion.RELEASE_11)
@AutoService(Processor.class)
public class SPPProcessor extends AbstractProcessor {

    LinkedHashMap<String,Object> docMap;

    public SPPProcessor() {
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
            yaml.dump(docMap,writer);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return true;
    }

    /**
     * 处理文档定义
     * @param defines 定义集合
     */
    private void processDefine(Set<? extends Element> defines){
        LinkedHashMap<String,String > info = new LinkedHashMap<>();
        for (Element service:defines){
            SPDocDefine docService = service.getAnnotation(SPDocDefine.class);
            String docName = docService.title().isEmpty()?service.getSimpleName().toString() : docService.title();
            String docDescription = docService.description().isEmpty()?"":docService.description();
            if(!docService.url().isEmpty()){
                docMap.put("servers",List.of(Map.of("url",docService.url(),"description","")));
            }
            info.put("title",docName);
            info.put("description",docDescription);
        }
        docMap.put("openapi","3.0.1");
        docMap.put("info",info);
    }


    /**
     * 处理所有的服务作为tag
     * @param services 服务集合
     */
    private void processService(Set<? extends Element> services){
        ArrayList<Map<String,String>> list = new ArrayList<>();
        for (Element service:services){

            SPDocService docService = service.getAnnotation(SPDocService.class);
            String serviceName = docService.name().isEmpty()?service.getSimpleName().toString() : docService.name();
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
            map.put("description",docConsumer.name());
            map.put(
                    "requestBody", Map.of(
                            "content",Map.of(
                                    "application/json",Map.of(
                                            "schema",Map.of(
                                                    "type","object",
                                                    "properties",processConsumerParam(consumer)

                                            )
                                    )
                            )
                    )
            );
            map.put(
                    "responses",Map.of(
                            "200", Map.of(
                                    "description","hello",
                                    "content",Map.of(
                                            "application/json",Map.of(
                                                    "schema",processConsumerReturn(consumer)
                                            )
                                    )
                            )
                    )
            );
            path.put(api,Map.of("post",map));


        }
        docMap.put("paths",path);
    }


    private LinkedHashMap<String,Object> processConsumerReturn(Element consumer){
        ExecutableElement executable = (ExecutableElement) consumer;
        TypeMirror returnType = executable.getReturnType();
        return typeMirrorToMap(returnType,"get",consumer.asType());
    }


    private LinkedHashMap<String,Object> processConsumerParam(Element consumer){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        ExecutableElement executable = (ExecutableElement) consumer;
        List<? extends VariableElement> parameters = executable.getParameters();
        if(parameters.size() > 0){
            if(parameters.size() == 1){
                map = typeMirrorToMap(parameters.get(0).asType(),"set",consumer.asType());
            }else{
                for (VariableElement variableElement:parameters){
                    map.put(variableElement.getSimpleName().toString(),typeMirrorToMap(variableElement.asType(),"set",consumer.asType()));
                }
            }
        }
        return map;
    }


    private LinkedHashMap<String,Object> declareTypeToMap(DeclaredType declaredType,String actionType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        for (Element element:declaredType.asElement().getEnclosedElements()){
            if(element.getKind() == ElementKind.FIELD){
                map.put(element.getSimpleName().toString(), typeMirrorToMap(element.asType(),actionType,declaredType));
            }
        }
        return map;
    }

    private LinkedHashMap<String,Object> typeMirrorToMap(TypeMirror parameter, String actionType,TypeMirror parentType){
        LinkedHashMap<String,Object> map = new LinkedHashMap<>();
        switch (parameter.getKind()){
            case DECLARED:{
                DeclaredType parameterType = (DeclaredType) parameter;
                if(parameterType.asElement().getEnclosingElement().toString().equals("java.lang")){
                    map = javaLangTypeToMap(parameterType);
                }else {
                    if(parameterType.asElement().getAnnotation(SPDoc.class) != null){
                        map.put("type",parameterType.asElement().getSimpleName().toString());
                        map.put("properties",declareTypeToMap(parameterType,actionType));
                    }
                }
                break;
            }
            case ARRAY:{
                map.put("type","array");
                map.put("items",typeMirrorToMap(((ArrayType) parameter).getComponentType(),actionType,parameter));
                break;
            }
            case INT:{
                map.put("type","integer");
                break;
            }
            case TYPEVAR:{
                if(parentType instanceof DeclaredType){
                    return typeMirrorToMap(((DeclaredType) parentType).getTypeArguments().get(0),actionType,parentType);
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
                map.put("description","Hello String");
                break;
            }
            case "Long":{
                map.put("type","integer");
            }
        }
        return map;
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
