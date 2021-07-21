package configgen.gencs;

import configgen.Logger;
import configgen.gen.*;
import configgen.util.CachedFiles;
import configgen.util.DomUtils;
import configgen.value.AllValue;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class GenPack extends Generator {

    private final File dstDir;
    private final String xml;
    private final boolean packAll;

    public GenPack(Parameter parameter) {
        super(parameter);
        dstDir = new File(parameter.get("dir", "cfg", "目录"));
        xml = parameter.get("xml", null, "描述分包策略的xml文件");
        packAll = parameter.has("packall", "是否全部打成一个包,这样不需要配置xml");

        parameter.end();
    }

    @Override
    public void generate(Context ctx) throws IOException {
        AllValue value = ctx.makeValue(filter);
        Map<String, Set<String>> packs = new HashMap<>();

        if (packAll) {
            packs.put("all", value.getTableNames());
        } else {
            File packXmlFile = xml != null ? new File(xml) : ctx.getDataDir().resolve("pack.xml").toFile();
            if (packXmlFile.exists()) {
                parsePack(packs, packXmlFile, value);
            } else {
                Logger.log(packXmlFile.getCanonicalPath() + "  not exist, pack to all.zip");
                packs.put("all", value.getTableNames());
            }
        }


        for (Map.Entry<String, Set<String>> entry : packs.entrySet()) {
            String packName = entry.getKey();
            Set<String> packCfgs = entry.getValue();
            if (!packCfgs.isEmpty()) {
                try (ZipOutputStream zos = createZip(new File(dstDir, packName + ".zip"))) {
                    ZipEntry ze = new ZipEntry(packName);
                    ze.setTime(0);
                    zos.putNextEntry(ze);
                    PackValueVisitor pack = new PackValueVisitor(zos);
                    for (String cfg : packCfgs) {
                        pack.addVTable(value.getVTable(cfg));
                    }
                }
            }
        }


        try (OutputStreamWriter writer = createUtf8Writer(new File(dstDir, "entry.txt"))) {
            writer.write(String.join(",", packs.keySet()));
        }

        CachedFiles.keepMetaAndDeleteOtherFiles(dstDir);
    }

    private void parsePack(Map<String, Set<String>> packs, File packXmlFile, AllValue value) {
        Set<String> source = new HashSet<>(value.getTableNames());
        Set<String> picked = new HashSet<>();

        Element root = DomUtils.rootElement(packXmlFile);
        DomUtils.permitElements(root, "pack");
        for (Element ep : DomUtils.elements(root, "pack")) {
            DomUtils.permitAttributes(ep, "name", "tables");
            String name = ep.getAttribute("name");
            String packName = name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
            require(!packName.equalsIgnoreCase("text"), "text.zip reserved for i18n");
            Set<String> packCfgs = new HashSet<>();

            for (String c : ep.getAttribute("tables").split(",")) {
                if (c.equals(".**")) {
                    packCfgs.addAll(source);
                    picked.addAll(source);
                    if (!source.isEmpty())
                        packs.put(packName, packCfgs);
                } else if (c.equals(".*")) {
                    int cnt = 0;
                    for (String n : source) {
                        if (!n.contains(".")) {
                            require(picked.add(n), n + " duplicate");
                            packCfgs.add(n);
                            cnt++;
                        }
                    }
                    if (cnt > 0)
                        packs.put(packName, packCfgs);
                    require(cnt > 0, c + " not exist");
                } else if (c.endsWith(".**")) {
                    String prefix = c.substring(0, c.length() - 2);
                    int cnt = 0;
                    for (String n : source) {
                        if (n.startsWith(prefix)) {
                            require(picked.add(n), n + " duplicate");
                            packCfgs.add(n);
                            cnt++;
                        }
                    }
                    if (cnt > 0)
                        packs.put(packName, packCfgs);
                } else if (c.endsWith(".*")) {
                    String prefix = c.substring(0, c.length() - 1);
                    int cnt = 0;
                    for (String n : source) {
                        if (n.startsWith(prefix) && !n.substring(prefix.length()).contains(".")) {
                            require(picked.add(n), n + " duplicate");
                            packCfgs.add(n);
                            cnt++;
                        }
                    }
                    if (cnt > 0)
                        packs.put(packName, packCfgs);
                } else {
                    require(picked.add(c), c + " duplicate");
                    packCfgs.add(c);
                    require(source.contains(c), c + " not exist");
                    packs.put(packName, packCfgs);
                }
            }
        }
        source.removeAll(picked);
        require(source.isEmpty(), source + " not contained in pack.xml");
    }
}
