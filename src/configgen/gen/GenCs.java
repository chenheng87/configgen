package configgen.gen;

import configgen.define.Field;
import configgen.type.*;
import configgen.value.CfgVs;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenCs extends Generator {
    private CfgVs value;
    private File dstDir;
    private String pkg;
    private String encoding;
    private String prefix;

    public GenCs() {
        providers.put("cs", this);
        Context.providers.put("cs", "cs,dir:Config,pkg:Config,encoding:GBK,prefix:Data    add ,own:x if need, cooperate with -gen bin, -gen pack");
    }

    @Override
    public void generate(Path configDir, CfgVs value, Context ctx) throws IOException {
        String _dir = ctx.get("dir", "Config");
        pkg = ctx.get("pkg", "Config");
        encoding = ctx.get("encoding", "GBK");
        prefix = ctx.get("prefix", "Data");
        String own = ctx.get("own", null);
        ctx.end();
        dstDir = Paths.get(_dir).resolve(pkg.replace('.', '/')).toFile();
        this.value = own != null ? extract(value, own) : value;

        CachedFileOutputStream.removeOtherFiles(dstDir);
        mkdirs(dstDir);

        copyFile("CSV.cs");
        copyFile("CSVLoader.cs");
        copyFile("LoadErrors.cs");
        copyFile("KeyedList.cs");
        genCSVLoaderDoLoad();

        for (TBean b : value.type.tbeans.values()) {
            genBean(b, null);
        }
        for (Cfg c : value.type.cfgs.values()) {
            genBean(c.tbean, c);
        }

        CachedFileOutputStream.doRemoveFiles();
    }

    private static class Name {
        final String pkg;
        final String className;
        final String fullName;
        final String path;

        Name(String topPkg, String prefix, String configName) {
            String[] seps = configName.split("\\.");
            String[] pks = new String[seps.length - 1];
            for (int i = 0; i < pks.length; i++)
                pks[i] = upper1Only(seps[i]);
            className = prefix + upper1Only(seps[seps.length - 1]);

            if (pks.length == 0)
                pkg = topPkg;
            else
                pkg = topPkg + "." + String.join(".", pks);

            if (pkg.isEmpty())
                fullName = className;
            else
                fullName = pkg + "." + className;

            if (pks.length == 0)
                path = className + ".cs";
            else
                path = String.join("/", pks) + "/" + className + ".cs";
        }
    }

    private void genBean(TBean tbean, Cfg cfg) throws IOException {
        Name name = new Name(pkg, prefix, tbean.define.name);
        File csFile = dstDir.toPath().resolve(name.path).toFile();
        mkdirs(csFile.getParentFile());
        try (PrintStream ps = cachedPrintStream(csFile, encoding)) {
            genBean(tbean, cfg, name, new TabPrintStream(ps));
        }
    }

    private void genBean(TBean tbean, Cfg cfg, Name name, TabPrintStream ps) {

        ps.println("using System;");
        ps.println("using System.Collections.Generic;");
        ps.println("using System.IO;");
        ps.println();

        ps.println("namespace " + name.pkg);
        ps.println("{");

        ps.println1("public partial class " + name.className);
        ps.println1("{");

        //static enum
        if (cfg != null && cfg.value.isEnum) {
            cfg.value.enumNames.forEach(e -> ps.println2("public static " + name.className + " " + upper1(e) + " { get; private set; }"));
            ps.println();
        }

        // property
        tbean.fields.forEach((n, t) -> {
            Field f = tbean.define.fields.get(n);
            String c = f.desc.isEmpty() ? "" : " // " + f.desc;
            ps.println2("public " + type(t) + " " + upper1(n) + " { get; private set; }" + c);
            t.constraint.refs.forEach(r -> ps.println2("public " + refType(t, r) + " " + refName(r) + " { get; private set; }"));
        });

        tbean.mRefs.forEach(m -> ps.println2("public " + fullName(m.ref) + " " + refName(m) + " { get; private set; }"));
        tbean.listRefs.forEach(l -> ps.println2("public List<" + fullName(l.ref) + "> " + refName(l) + " { get; private set; }"));
        ps.println();

        //constructor
        if (cfg == null) {
            ps.println2("public " + name.className + "() {");
            ps.println2("}");
            ps.println();

            ps.println2("public " + name.className + "(" + formalParams(tbean.fields) + ") {");
            tbean.fields.forEach((n, t) -> ps.println3("this." + upper1(n) + " = " + lower1(n) + ";"));
            ps.println2("}");
            ps.println();
        }

        //hash
        Map<String, Type> keys = cfg != null ? cfg.keys : tbean.fields;
        ps.println2("public override int GetHashCode()");
        ps.println2("{");
        ps.println3("return " + hashCodes(keys) + ";");
        ps.println2("}");
        ps.println();

        //equal
        ps.println2("public override bool Equals(object obj)");
        ps.println2("{");
        ps.println3("if (obj == null) return false;");
        ps.println3("if (obj == this) return true;");
        ps.println3("var o = obj as " + name.className + ";");
        ps.println3("return o != null && " + equals(keys) + ";");
        ps.println2("}");
        ps.println();

        //toString
        ps.println2("public override string ToString()");
        ps.println2("{");
        ps.println3("return \"(\" + " + toStrings(tbean.fields) + " + \")\";");
        ps.println2("}");
        ps.println();

        String csv = "\"" + tbean.define.name + "\"";
        if (cfg != null) {
            //static class Key
            if (keys.size() > 1) {
                ps.println2("class Key");
                ps.println2("{");
                keys.forEach((n, t) -> ps.println3("readonly " + type(t) + " " + upper1(n) + ";"));
                ps.println();

                ps.println3("public Key(" + formalParams(keys) + ")");
                ps.println3("{");
                keys.forEach((n, t) -> ps.println4("this." + upper1(n) + " = " + lower1(n) + ";"));
                ps.println3("}");
                ps.println();

                ps.println3("public override int GetHashCode()");
                ps.println3("{");
                ps.println4("return " + hashCodes(keys) + ";");
                ps.println3("}");

                ps.println3("public override bool Equals(object obj)");
                ps.println3("{");
                ps.println4("if (obj == null) return false;");
                ps.println4("if (obj == this) return true;");
                ps.println4("var o = obj as Key;");
                ps.println4("return o != null && " + equals(keys) + ";");
                ps.println3("}");

                ps.println2("}");
                ps.println();
            }

            //static all
            String keyType = keys.size() > 1 ? "Key" : type(keys.values().iterator().next());
            String allType = "Config.KeyedList<" + keyType + ", " + name.className + ">";
            ps.println2("static " + allType + " all = null;");
            ps.println();
            ps.println2("public static List<" + name.className + "> All()");
            ps.println2("{");
            ps.println3("return all.OrderedValues;");
            ps.println2("}");
            ps.println();

            //static get
            ps.println2("public static " + name.className + " Get(" + formalParams(keys) + ")");
            ps.println2("{");
            ps.println3(name.className + " v;");
            ps.println3("return all.TryGetValue(" + actualParamsKey(keys) + ", out v) ? v : null;");
            ps.println2("}");
            ps.println();

            // static filter
            ps.println2("public static List<" + name.className + "> Filter(Predicate<" + name.className + "> predicate)");
            ps.println2("{");
            ps.println3("var r = new List<" + name.className + ">();");
            ps.println3("foreach (var e in all.OrderedValues)");
            ps.println3("{");
            ps.println4("if (predicate(e))");
            ps.println5("r.Add(e);");
            ps.println3("}");
            ps.println3("return r;");
            ps.println2("}");
            ps.println();

            //static initialize
            ps.println2("internal static void Initialize(int count, BinaryReader br, Dictionary<ushort, string> map, Config.LoadErrors errors)");
            ps.println2("{");
            ps.println3("all = new " + allType + "();");
            ps.println3("for (var i = 0; i < count; i++) {");
            ps.println4("var self = _create(br, map);");
            ps.println4("all.Add(" + actualParamsKeySelf(keys) + ", self);");

            if (cfg.value.isEnum) {
                String ef = upper1(cfg.define.enumStr);
                ps.println4("if (self." + ef + ".Trim().Length == 0)");
                ps.println5("continue;");
                ps.println4("switch(self." + ef + ".Trim())");
                ps.println4("{");
                cfg.value.enumNames.forEach(e -> {
                    ps.println5("case \"" + e + "\":");
                    ps.println6("if (" + upper1(e) + " != null)");
                    ps.println7("errors.EnumDup(" + csv + ", self.ToString());");
                    ps.println6(upper1(e) + " = self;");
                    ps.println6("break;");
                });
                ps.println5("default:");
                ps.println6("errors.EnumDataAdd(" + csv + ", self.ToString());");
                ps.println6("break;");
                ps.println4("}");
            }
            ps.println3("}");

            if (cfg.value.isEnum) {
                cfg.value.enumNames.forEach(e -> {
                    ps.println3("if (" + upper1(e) + " == null)");
                    ps.println4("errors.EnumNull(" + csv + ", \"" + e + "\");");
                });
            }
            ps.println2("}");
            ps.println();

            //static resolve
            if (tbean.hasRef()) {
                ps.println2("internal static void Resolve(Config.LoadErrors errors) {");
                ps.println3("foreach (var v in All())");
                ps.println4("v._resolve(errors);");
                ps.println2("}");
                ps.println();
            }
        } // end cfg != null

        //static create
        ps.println2("internal static " + name.className + " _create(BinaryReader br, Dictionary<ushort, string> map)");
        ps.println2("{");
        ps.println3("var self = new " + name.className + "();");
        tbean.fields.forEach((n, t) -> {
            if (t instanceof TList) {
                ps.println3("self." + upper1(n) + " = new " + type(t) + "();");
                ps.println3("for (var c = (int)br.ReadUInt16(); c > 0; c--)");
                ps.println4("self." + upper1(n) + ".Add(" + _create(((TList) t).value) + ");");
            } else if (t instanceof TMap) {
                ps.println3("self." + upper1(n) + " = new " + type(t) + "();");
                ps.println3("for (var c = (int)br.ReadUInt16(); c > 0; c--)");
                ps.println4("self." + upper1(n) + ".Add(" + _create(((TMap) t).key) + ", " + _create(((TMap) t).value) + ");");
            } else {
                ps.println3("self." + upper1(n) + " = " + _create(t) + ";");
            }
        });
        ps.println3("return self;");
        ps.println2("}");
        ps.println();

        //resolve
        if (tbean.hasRef()) {
            ps.println2("internal void _resolve(Config.LoadErrors errors)");
            ps.println2("{");
            tbean.fields.forEach((n, t) -> {
                if (t.hasRef()) {
                    String field = "\"" + n + "\"";
                    if (t instanceof TList) {
                        TList tt = (TList) t;
                        if (tt.value instanceof TBean && tt.value.hasRef()) {
                            ps.println3("foreach (var e in " + upper1(n) + ")");
                            ps.println3("{");
                            ps.println4("e._resolve(errors);");
                            ps.println3("}");
                        }

                        for (SRef sr : t.constraint.refs) {
                            ps.println3(refName(sr) + " = new " + refType(t, sr) + "();");
                            ps.println3("foreach (var e in " + upper1(n) + ")");
                            ps.println3("{");
                            ps.println4("var r = " + fullName(sr.ref) + ".Get(e);");
                            ps.println4("if (r == null) errors.RefNull(" + csv + ", ToString() , " + field + ", e);");
                            ps.println4(refName(sr) + ".Add(r);");
                            ps.println3("}");
                        }
                    } else if (t instanceof TMap) {
                        TMap tt = (TMap) t;
                        if ((tt.key instanceof TBean && tt.key.hasRef()) || (tt.value instanceof TBean && tt.value.hasRef())) {
                            ps.println3("foreach (var kv in " + upper1(n) + ".Map)");
                            ps.println3("{");
                            if (tt.key instanceof TBean && tt.key.hasRef()) {
                                ps.println4("kv.Key._resolve(errors);");
                            }
                            if (tt.value instanceof TBean && tt.value.hasRef()) {
                                ps.println4("kv.Value._resolve(errors);");
                            }
                            ps.println3("}");
                        }
                        for (SRef sr : t.constraint.refs) {
                            ps.println3(refName(sr) + " = new " + refType(t, sr) + "();");
                            ps.println3("foreach (var kv in " + upper1(n) + ".Map)");
                            ps.println3("{");

                            if (sr.keyRef != null) {
                                ps.println4("var k = " + fullName(sr.keyRef) + ".Get(kv.Key);");
                                ps.println4("if (k == null) errors.RefKeyNull(" + csv + ", ToString(), " + field + ", kv.Key);");
                            } else {
                                ps.println4("var k = kv.Key;");
                            }

                            if (sr.ref != null) {
                                ps.println4("var v = " + fullName(sr.ref) + ".Get(kv.Value);");
                                ps.println4("if (v == null) errors.RefNull(" + csv + ", ToString(), " + field + ", kv.Value);");
                            } else {
                                ps.println4("var v = kv.Value;");
                            }
                            ps.println4(refName(sr) + ".Add(k, v);");
                            ps.println3("}");
                        }
                    } else {
                        if (t instanceof TBean && t.hasRef()) {
                            ps.println3(upper1(n) + "._resolve(errors);");
                        }

                        for (SRef sr : t.constraint.refs) {
                            ps.println3(refName(sr) + " = " + fullName(sr.ref) + ".Get(" + upper1(n) + ");");
                            if (!sr.nullable)
                                ps.println3("if (" + refName(sr) + " == null) errors.RefNull(" + csv + ", ToString(), " + field + ", " + upper1(n) + ");");
                        }
                    }
                }
            });


            tbean.mRefs.forEach(m -> {
                ps.println3(refName(m) + " = " + fullName(m.ref) + ".Get(" + actualParams(m.define.keys) + ");");
                if (!m.define.nullable)
                    ps.println3("if (" + refName(m) + " == null) errors.RefNull(" + csv + ", ToString(), \"" + m.define.name + "\", 0);");
            });

            tbean.listRefs.forEach(l -> {
                ps.println3(refName(l) + " = new List<" + fullName(l.ref) + ">();");
                ps.println3("foreach (var v in " + fullName(l.ref) + ".All())");
                ps.println3("{");
                List<String> eqs = new ArrayList<>();
                for (int i = 0; i < l.keys.length; i++) {
                    String k = l.keys[i];
                    String rk = l.refKeys[i];
                    eqs.add("v." + upper1(rk) + ".Equals(" + upper1(k) + ")");
                }
                ps.println3("if (" + String.join(" && ", eqs) + ")");
                ps.println4(refName(l) + ".Add(v);");
                ps.println3("}");
            });

            ps.println("	    }");
            ps.println();
        }

        ps.println("    }");
        ps.println("}");

    }

    private String formalParams(Map<String, Type> fs) {
        return String.join(", ", fs.entrySet().stream().map(e -> type(e.getValue()) + " " + lower1(e.getKey())).collect(Collectors.toList()));
    }

    private String actualParams(String[] keys) {
        return String.join(", ", Arrays.asList(keys).stream().map(Generator::upper1).collect(Collectors.toList()));
    }

    private String actualParamsKey(Map<String, Type> keys) {
        String p = String.join(", ", keys.keySet().stream().map(Generator::lower1).collect(Collectors.toList()));
        return keys.size() > 1 ? "new Key(" + p + ")" : p;
    }

    private String actualParamsKeySelf(Map<String, Type> keys) {
        String p = String.join(", ", keys.keySet().stream().map(n -> "self." + upper1(n)).collect(Collectors.toList()));
        return keys.size() > 1 ? "new Key(" + p + ")" : p;
    }

    private String equals(Map<String, Type> fs) {
        return String.join(" && ", fs.entrySet().stream().map(e -> upper1(e.getKey()) + ".Equals(o." + upper1((e.getKey())) + ")").collect(Collectors.toList()));
    }

    private String hashCodes(Map<String, Type> fs) {
        return String.join(" + ", fs.entrySet().stream().map(e -> upper1(e.getKey()) + ".GetHashCode()").collect(Collectors.toList()));
    }

    private String toStrings(Map<String, Type> fs) {
        return String.join(" + \",\" + ", fs.entrySet().stream().map(e -> toString(e.getKey(), e.getValue())).collect(Collectors.toList()));
    }

    private String toString(String n, Type t) {
        if (t instanceof TList || t instanceof TMap)
            return "CSV.ToString(" + upper1(n) + ")";
        else
            return upper1(n);
    }

    private String refType(Type t, SRef ref) {
        if (t instanceof TList) {
            return "List<" + fullName(ref.ref) + ">";
        } else if (t instanceof TMap) {
            return "KeyedList<"
                    + (ref.keyRef != null ? fullName(ref.keyRef) : type(((TMap) t).key)) + ", "
                    + (ref.ref != null ? fullName(ref.ref) : type(((TMap) t).value)) + ">";
        } else {
            return fullName(ref.ref);
        }
    }

    private String refName(SRef sr) {
        return (sr.nullable ? "NullableRef" : "Ref") + upper1(sr.name);
    }

    private String refName(MRef mr) {
        return (mr.define.nullable ? "NullableRef" : "Ref") + upper1(mr.define.name);
    }

    private String refName(ListRef lr) {
        return "ListRef" + upper1(lr.name);
    }

    private String fullName(TBean tbean) {
        return new Name(pkg, prefix, tbean.define.name).fullName;
    }

    private String fullName(Cfg cfg) {
        return fullName(cfg.tbean);
    }

    private String type(Type t) {
        return t.accept(new TypeVisitorT<String>() {
            @Override
            public String visit(TBool type) {
                return "bool";
            }

            @Override
            public String visit(TInt type) {
                return "int";
            }

            @Override
            public String visit(TLong type) {
                return "long";
            }

            @Override
            public String visit(TFloat type) {
                return "float";
            }

            @Override
            public String visit(TString type) {
                return "string";
            }

            @Override
            public String visit(TList type) {
                return "List<" + type(type.value) + ">";
            }

            @Override
            public String visit(TMap type) {
                return "KeyedList<" + type(type.key) + ", " + type(type.value) + ">";
            }

            @Override
            public String visit(TBean type) {
                return fullName(type);
            }
        });
    }

    private String _create(Type t) {
        return t.accept(new TypeVisitorT<String>() {
            @Override
            public String visit(TBool type) {
                return "br.ReadBoolean()";
            }

            @Override
            public String visit(TInt type) {
                return "br.ReadInt32()";
            }

            @Override
            public String visit(TLong type) {
                return "br.ReadInt64()";
            }

            @Override
            public String visit(TFloat type) {
                return "br.ReadSingle()";
            }

            @Override
            public String visit(TString type) {
                return type.subtype == TString.Subtype.STRING ? "Config.CSV.ReadString(br)" : "Config.CSV.ReadText(br, map)";
            }

            @Override
            public String visit(TList type) {
                return null;
            }

            @Override
            public String visit(TMap type) {
                return null;
            }

            @Override
            public String visit(TBean type) {
                return fullName(type) + "._create(br, map)";
            }
        });
    }

    private void copyFile(String file) throws IOException {
        try (InputStream is = getClass().getResourceAsStream("/support/" + file);
             BufferedReader br = new BufferedReader(new InputStreamReader(is != null ? is : new FileInputStream("src/support/" + file), "GBK"));
             PrintStream ps = cachedPrintStream(new File(dstDir, file), encoding)) {
            for (String line = br.readLine(); line != null; line = br.readLine()) {
                ps.println(line);
            }
        }
    }

    private void genCSVLoaderDoLoad() throws IOException {
        try (PrintStream stream = cachedPrintStream(new File(dstDir, "CSVLoaderDoLoad.cs"), encoding)) {
            TabPrintStream ps = new TabPrintStream(stream);
            ps.println("using System.Collections.Generic;");
            ps.println("using System.IO;");
            ps.println();

            ps.println("namespace Config");
            ps.println("{");

            ps.println1("public static partial class CSVLoader {");
            ps.println();
            ps.println2("public static LoadErrors DoLoad(List<BinaryReader> byterList, Dictionary<string, Dictionary<ushort, string>> allTextMap)");
            ps.println2("{");
            ps.println3("var errors = new LoadErrors();");
            ps.println3("var configNulls = new List<string>");
            ps.println3("{");
            for (String name : value.type.cfgs.keySet()) {
                ps.println4("\"" + name + "\",");
            }
            ps.println3("};");

            ps.println3("foreach (var byter in byterList)");
            ps.println3("{");
            ps.println3("for(;;)");
            ps.println3("{");
            ps.println4("try");
            ps.println4("{");
            ps.println5("var csv = CSV.ReadString(byter);");
            ps.println5("var count = byter.ReadUInt16();");
            ps.println5("Dictionary<ushort, string> textMap;");
            ps.println5("allTextMap.TryGetValue(csv, out textMap);");

            ps.println5("switch(csv)");
            ps.println5("{");

            value.type.cfgs.forEach((name, cfg) -> {
                ps.println6("case \"" + name + "\":");
                ps.println7("configNulls.Remove(csv);");
                ps.println7(fullName(cfg.tbean) + ".Initialize(count, byter, textMap, errors);");
                ps.println7("break;");
            });

            ps.println6("default:");
            ps.println7("errors.ConfigDataAdd(csv);");
            ps.println7("break;");
            ps.println5("}");

            ps.println4("}");
            ps.println4("catch (EndOfStreamException)");
            ps.println4("{");
            ps.println5("break;");
            ps.println4("}");

            ps.println3("}");
            ps.println3("}");

            ps.println3("foreach (var csv in configNulls)");
            ps.println4("errors.ConfigNull(csv);");

            value.type.cfgs.forEach((n, c) -> {
                if (c.tbean.hasRef()) {
                    ps.println3(fullName(c) + ".Resolve(errors);");
                }
            });

            ps.println3("return errors;");
            ps.println2("}");
            ps.println();
            ps.println1("}");
            ps.println("}");
            ps.println();
        }
    }

}