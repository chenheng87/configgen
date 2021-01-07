package configgen.define;

import configgen.Node;
import configgen.data.AllData;
import configgen.data.DTable;
import configgen.type.AllType;
import configgen.util.DomUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class AllDefine extends Node {
    private Path xmlPath;
    private String encoding;

    private String dataDirStr;
    private Path dataDir;

    private final Map<String, Bean> beans = new TreeMap<>();
    private final Map<String, Table> tables = new TreeMap<>();
    /**
     * import其他文件，其他文件也可再import。
     * 方便xml文件的组织，你可以全部csv就一个xml，也可以每个文件夹一个xml，最后总的一个xml来汇总。
     * 比如可以区分客户端用的xml，服务器用的xml。所以这里就存在抽取data的两个机制，一个xml，一个own，任君选择
     */
    private final Map<String, Import> imports = new TreeMap<>();


    /**
     * 要对上层隐藏import的机制，这里为效率cache下来。
     */
    private final Map<String, Bean> cachedAllBeans = new TreeMap<>();
    private final Map<String, Table> cachedAllTables = new TreeMap<>();

    /**
     * fullDefine才有，所谓full就是全部的定义，不能是从own抽取的
     */
    private AllData thisData;
    private final Map<String, DTable> cachedAllDataTables = new TreeMap<>();


    public AllDefine(Path _xmlPath, String _encoding) {
        super(null, "AllDefine");
        xmlPath = _xmlPath;
        encoding = _encoding;
        dataDirStr = ".";

        if (!Files.exists(xmlPath)) {
            return;
        }

        Element self = DomUtils.rootElement(xmlPath.toFile());
        DomUtils.permitAttributes(self, "datadir");

        if (self.hasAttribute("datadir")) {
            dataDirStr = self.getAttribute("datadir");
            dataDir = resolvePath(dataDirStr);
        } else {
            dataDir = xmlPath.getParent(); //默认当前xml文件所在目录
        }
        DomUtils.permitElements(self, "import", "bean", "table");

        for (Element e : DomUtils.elements(self, "import")) {
            Import imp = new Import(this, e, encoding);
            require(null == imports.put(imp.file, imp), "import file重复", imp.file);
        }

        for (Element e : DomUtils.elements(self, "bean")) {
            Bean b = new Bean(this, e);
            require(null == beans.put(b.name, b), "Bean定义名字重复", b.name);
        }

        for (Element e : DomUtils.elements(self, "table")) {
            Table t = new Table(this, e);
            require(null == tables.put(t.name, t), "表定义名字重复", t.name);
            require(!beans.containsKey(t.name), "表和Bean定义名字重复", t.name);
        }

        updateCache();
    }

    private void updateCache() {
        cachedAllBeans.clear();
        cachedAllBeans.putAll(beans);
        for (Import imp : imports.values()) {
            cachedAllBeans.putAll(imp.define.cachedAllBeans);
        }

        cachedAllTables.clear();
        cachedAllTables.putAll(tables);
        for (Import imp : imports.values()) {
            cachedAllTables.putAll(imp.define.cachedAllTables);
        }
    }

    Path resolvePath(String file) {
        return xmlPath.getParent().resolve(file);
    }


    //////////////////////////////// 对上层接口，隐藏import导致的层级Data和层级Table


    public Path getDataDir() {
        return dataDir;
    }

    public Collection<Bean> getAllBeans() {
        return cachedAllBeans.values();
    }

    public Collection<Table> getAllTables() {
        return cachedAllTables.values();
    }

    public Table getTable(String tableName) {
        return cachedAllTables.get(tableName);
    }

    public DTable getDTable(String tableName) {
        return cachedAllDataTables.get(tableName);
    }


    //////////////////////////////// 读取数据文件，并补充完善Define

    /**
     *  读取数据文件，并补充完善Define，解析带类型的fullType
     */
    public AllType readData_AutoFix_ResolveType() {
        AllType firstTryType = new AllType(this);
        firstTryType.resolve();

        readDataFilesAndAutoFix(firstTryType);

        saveToXml();

        return resolveFullTypeAndAttachToData();
    }


    /**
     * 自动从Data中提取头两行的定义信息，填充Define
     */
    private void readDataFilesAndAutoFix(AllType firstTryType) {
        for (Import imp : imports.values()) {
            imp.define.readDataFilesAndAutoFix(firstTryType);
        }

        thisData = new AllData(dataDir, encoding);
        thisData.autoFixDefine(this, firstTryType);


        updateCache();
        cachedAllDataTables.clear();
        cachedAllDataTables.putAll(thisData.getDTables());
        for (Import imp : imports.values()) {
            cachedAllDataTables.putAll(imp.define.cachedAllDataTables);
        }
    }

    /**
     * 保存回xml
     */
    public void saveToXml() {
        for (Import imp : imports.values()) {
            imp.define.saveToXml();
        }
        save();
    }

    /**
     * 解析出类型，把齐全的类型信息 赋到 Data上，因为之后生成Value时可能只会用 不全的Type
     */
    private AllType resolveFullTypeAndAttachToData() {
        AllType fullType = new AllType(this);
        fullType.resolve();
        attachTypeToData(fullType);
        return fullType;
    }

    void attachTypeToData(AllType type) {
        thisData.attachType(type);
        for (Import imp : imports.values()) {
            imp.define.attachTypeToData(type);
        }
    }


    //////////////////////////////// auto fix使用的接口

    public Set<String> getTableNames() {
        return new HashSet<>(tables.keySet());
    }

    public void removeTable(String tableName) {
        tables.remove(tableName);
    }

    public Table newTable(String tableName) {
        Table t = new Table(this, tableName);
        tables.put(tableName, t);
        return t;
    }

    //////////////////////////////// extract

    private AllDefine(String own) {
        super(null, "AllDefine(" + own + ")");
    }


    /**
     * @param own 配置为own="client,xeditor"的column就会被resolvePartType("client")抽取出来
     * @return 一个抽取过后的带类型结构信息。
     * 用于对上层隐藏掉own的机制。
     */
    // 返回的是全新的 部分的Type
    public AllType resolvePartType(String own) {
        AllDefine topPart = extract(own);
        topPart.resolveExtract(topPart);
        AllType ownType = new AllType(topPart);
        ownType.resolve();
        return ownType;
    }


    AllDefine extract(String own) {
        AllDefine part = new AllDefine(own);

        for (Import imp : imports.values()) {
            Import pi = imp.extract(part, own);
            part.imports.put(pi.file, pi);
        }

        for (Bean bean : beans.values()) {
            try {
                Bean pb = bean.extract(part, own);
                if (pb != null)
                    part.beans.put(bean.name, pb);
            } catch (Throwable e) {
                throw new AssertionError(bean.name + ",从这个结构体抽取[" + own + "]出错", e);
            }
        }

        for (Table table : tables.values()) {
            try {
                Table pc = table.extract(part, own);
                if (pc != null)
                    part.tables.put(table.name, pc);
            } catch (Throwable e) {
                throw new AssertionError(table.name + ",从这个表结构抽取[" + own + "]出错", e);
            }
        }

        part.updateCache();
        return part;
    }

    void resolveExtract(AllDefine top) {
        for (Import imp : imports.values()) {
            imp.resolveExtract(top);
        }

        for (Bean bean : beans.values()) {
            try {
                bean.resolveExtract(top);
            } catch (Throwable e) {
                throw new AssertionError(bean.name + ",解析这个结构体抽取部分出错", e);
            }
        }
        for (Table table : tables.values()) {
            try {
                table.resolveExtract(top);
            } catch (Throwable e) {
                throw new AssertionError(table.name + ",解析这个表结构抽取部分出错", e);
            }
        }
    }


    //////////////////////////////// save

    private void save() {
        Document doc = DomUtils.newDocument();

        Element self = doc.createElement("db");
        doc.appendChild(self);
        self.setAttribute("datadir", dataDirStr);

        for (Import imp : imports.values()) {
            imp.save(self);
        }

        for (Bean b : beans.values()) {
            b.save(self);
        }
        for (Table t : tables.values()) {
            t.save(self);
        }

        DomUtils.prettySaveDocument(doc, xmlPath.toFile(), encoding);
    }


}
