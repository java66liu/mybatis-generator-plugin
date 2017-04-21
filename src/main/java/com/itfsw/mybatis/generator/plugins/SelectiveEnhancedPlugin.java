/*
 * Copyright (c) 2017.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.itfsw.mybatis.generator.plugins;

import com.itfsw.mybatis.generator.plugins.utils.CommentTools;
import com.itfsw.mybatis.generator.plugins.utils.PluginTools;
import org.mybatis.generator.api.IntrospectedTable;
import org.mybatis.generator.api.PluginAdapter;
import org.mybatis.generator.api.dom.java.*;
import org.mybatis.generator.api.dom.xml.*;
import org.mybatis.generator.internal.util.StringUtility;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ---------------------------------------------------------------------------
 * Selective 增强插件
 * ---------------------------------------------------------------------------
 * @author: hewei
 * @time:2017/4/20 15:39
 * ---------------------------------------------------------------------------
 */
public class SelectiveEnhancedPlugin extends PluginAdapter {
    private static final Logger logger = LoggerFactory.getLogger(SelectiveEnhancedPlugin.class);

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean validate(List<String> warnings) {
        // 插件使用前提是targetRuntime为MyBatis3
        if (StringUtility.stringHasValue(getContext().getTargetRuntime()) && "MyBatis3".equalsIgnoreCase(getContext().getTargetRuntime()) == false) {
            logger.warn("itfsw:插件" + this.getClass().getTypeName() + "要求运行targetRuntime必须为MyBatis3！");
            return false;
        }

        // 插件使用前提是使用了ModelColumnPlugin插件
        if (!PluginTools.checkDependencyPlugin(ModelColumnPlugin.class, getContext())) {
            logger.warn("itfsw:插件" + this.getClass().getTypeName() + "插件需配合com.itfsw.mybatis.generator.plugins.ModelColumnPlugin插件使用！");
            return false;
        }

        return true;
    }

    /**
     * Model Methods 生成
     * 具体执行顺序 http://www.mybatis.org/generator/reference/pluggingIn.html
     * @param topLevelClass
     * @param introspectedTable
     * @return
     */
    @Override
    public boolean modelBaseRecordClassGenerated(TopLevelClass topLevelClass, IntrospectedTable introspectedTable) {
        // import
        topLevelClass.addImportedType(FullyQualifiedJavaType.getNewMapInstance());
        topLevelClass.addImportedType(FullyQualifiedJavaType.getNewHashMapInstance());

        // field
        Field selectiveColumnsField = new Field("selectiveColumns", new FullyQualifiedJavaType("Map<String, Boolean>"));
        CommentTools.addFieldComment(selectiveColumnsField, introspectedTable);
        selectiveColumnsField.setVisibility(JavaVisibility.PRIVATE);
        selectiveColumnsField.setInitializationString("new HashMap<String, Boolean>()");
        topLevelClass.addField(selectiveColumnsField);

        // Method isSelective
        Method mIsSelective = new Method("isSelective");
        CommentTools.addMethodComment(mIsSelective, introspectedTable);
        mIsSelective.setVisibility(JavaVisibility.PUBLIC);
        mIsSelective.setReturnType(FullyQualifiedJavaType.getBooleanPrimitiveInstance());
        mIsSelective.addParameter(new Parameter(FullyQualifiedJavaType.getStringInstance(), "column"));
        mIsSelective.addBodyLine("if (column == null) {");
        mIsSelective.addBodyLine("return this.selectiveColumns.size() > 0;");
        mIsSelective.addBodyLine("} else {");
        mIsSelective.addBodyLine("return this.selectiveColumns.get(column) != null;");
        mIsSelective.addBodyLine("}");
        topLevelClass.addMethod(mIsSelective);

        // Method selective
        Method mSelective = new Method("selective");
        CommentTools.addMethodComment(mSelective, introspectedTable);
        mSelective.setVisibility(JavaVisibility.PUBLIC);
        mSelective.setReturnType(topLevelClass.getType());
        mSelective.addParameter(new Parameter(new FullyQualifiedJavaType(ModelColumnPlugin.ENUM_NAME), "columns", true));
        mSelective.addBodyLine("this.selectiveColumns.clear();");
        mSelective.addBodyLine("if (columns != null) {");
        mSelective.addBodyLine("for (" + ModelColumnPlugin.ENUM_NAME + " column : columns) {");
        mSelective.addBodyLine("this.selectiveColumns.put(column.value(), true);");
        mSelective.addBodyLine("}");
        mSelective.addBodyLine("}");
        mSelective.addBodyLine("return this;");
        topLevelClass.addMethod(mSelective);

        return true;
    }

    /**
     * SQL Map Methods 生成
     * 具体执行顺序 http://www.mybatis.org/generator/reference/pluggingIn.html
     * @param document
     * @param introspectedTable
     * @return
     */
    @Override
    public boolean sqlMapDocumentGenerated(Document document, IntrospectedTable introspectedTable) {
        List<Element> rootElements = document.getRootElement().getElements();
        for (Element rootElement : rootElements) {
            if (rootElement instanceof XmlElement) {
                XmlElement xmlElement = (XmlElement) rootElement;
                List<Attribute> attributes = xmlElement.getAttributes();
                // 查找ID
                String id = "";
                for (Attribute attribute : attributes) {
                    if (attribute.getName().equals("id")) {
                        id = attribute.getValue();
                    }
                }

                // ====================================== 1. insertSelective ======================================
                if ("insertSelective".equals(id)) {
                    List<XmlElement> trimEles = this.findEle(xmlElement, "trim");
                    for (XmlElement ele : trimEles) {
                        this.replaceEle(ele, "_parameter.");
                    }
                }
            }
        }
        return true;
    }

    /**
     * 查找当前节点下的指定节点
     * @param element
     * @param eleName
     * @return
     */
    private List<XmlElement> findEle(XmlElement element, String eleName) {
        List<XmlElement> list = new ArrayList<>();
        List<Element> elements = element.getElements();
        for (Element ele : elements) {
            if (ele instanceof XmlElement) {
                XmlElement xmlElement = (XmlElement) ele;
                if (eleName.equalsIgnoreCase(xmlElement.getName())) {
                    list.add(xmlElement);
                }
            }
        }
        return list;
    }

    /**
     * 替换节点if信息
     * @param element
     * @param prefix
     */
    private void replaceEle(XmlElement element, String prefix) {
        // choose
        XmlElement chooseEle = new XmlElement("choose");
        // when
        XmlElement whenEle = new XmlElement("when");
        whenEle.addAttribute(new Attribute("test", prefix + "isSelective()"));
        for (Element ele : element.getElements()) {
            // if的text节点
            XmlElement xmlElement = (XmlElement) ele;
            TextElement textElement = (TextElement) xmlElement.getElements().get(0);

            // 找出field 名称
            String text = textElement.getContent().trim();
            String field = "";
            if (text.matches(".*\\s*=\\s*#\\{.*\\},?")) {
                Pattern pattern = Pattern.compile("(.*)\\s*=\\s*#\\{.*},?");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()){
                    field = matcher.group(1);
                }
            } else if (text.matches("#\\{.*\\},?")) {
                Pattern pattern = Pattern.compile("#\\{(.*?),.*\\},?");
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()){
                    field = matcher.group(1);
                }
            } else {
                field = text.replaceAll(",", "");
            }

            XmlElement ifEle = new XmlElement("if");
            ifEle.addAttribute(new Attribute("test", prefix + "isSelective(" + field + ")"));
            ifEle.addElement(textElement);
            whenEle.addElement(ifEle);
        }

        // otherwise
        XmlElement otherwiseEle = new XmlElement("otherwise");
        for (Element ele : element.getElements()) {
            otherwiseEle.addElement(ele);
        }

        chooseEle.addElement(whenEle);
        chooseEle.addElement(otherwiseEle);

        // 清空原始节点，新增choose节点
        element.getElements().clear();
        element.addElement(chooseEle);
    }
}
