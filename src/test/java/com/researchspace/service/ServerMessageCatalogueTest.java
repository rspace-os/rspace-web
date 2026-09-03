package com.researchspace.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import java.io.IOException;
import java.net.URI;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.lang.model.element.Modifier;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.springframework.context.NoSuchMessageException;

class ServerMessageCatalogueTest {

  private static final Path LOCALE_DIRECTORY =
      Path.of("src/main/webapp/ui/src/modules/common/i18n/locales/en-US");
  private static final Path JAVA_SOURCE_DIRECTORY = Path.of("src/main/java");
  private static final Locale ENGLISH = Locale.forLanguageTag("en-US");
  private static final Pattern LOWER_CAMEL_CASE_KEY_COMPONENT =
      Pattern.compile("[a-z][A-Za-z0-9]*");
  private static final Pattern LOWERCASE_FIELD_SUFFIX = Pattern.compile(".+field(?:Name|Value).*");
  private static final Pattern VALIDATION_KEY =
      Pattern.compile("\\{([A-Za-z0-9_-]+(?:[.:][A-Za-z0-9_-]+)+)\\}");
  private static final Set<String> MESSAGE_PROVIDER_TYPES =
      Set.of(
          "ApplicationContext",
          "JsonMessageSource",
          "LocaleBoundMessages",
          "MessageSource",
          "MessageSourceAccessor",
          "MessageSourceUtils",
          "WebApplicationContext");
  private static final Set<String> MESSAGE_CONSTRUCTOR_TYPES =
      Set.of(
          "ApiAuthenticationException",
          "ApiRuntimeException",
          "ChemistryClientException",
          "ExportableInvRecProperty",
          "GroupType",
          "MediaContentMismatchException",
          "MessageType",
          "RemovalCircumstancesMessage",
          "RoleInGroup");
  private static final Map<String, Integer> MESSAGE_KEY_ARGUMENTS =
      Map.ofEntries(
          Map.entry("UserRoleHandlerImpl#assertAuthorizationAndInitUser", 2),
          Map.entry("IUserPermissionUtils#assertHasPermissionsOnTargetUser", 2),
          Map.entry("UserPermissionUtils#assertHasPermissionsOnTargetUser", 2),
          Map.entry("FilestoreAclChecker#denied", 2),
          Map.entry("MediaManagerImpl#getFieldAndAssertAuthorised", 2),
          Map.entry("RSChemElementManagerImpl#getFieldAndAssertAuthorised", 2),
          Map.entry("CloudNotificationManagerImpl#mergeIntoVelocityTemplates", 3),
          Map.entry("CloudNotificationManagerImpl#mergeExistingUserTemplate", 1),
          Map.entry("CloudNotificationManagerImpl#mergeTempUserTemplate", 2),
          Map.entry("UserRoleHandlerImpl#sentHtmlEmailLogAnyException", 0),
          Map.entry("GroupPermissionUtils#throwAuthException", 1),
          Map.entry("UserValidator#addToErrorsAndGetMessage", 2),
          Map.entry("BaseController#getErrorListFromMessageCode", 0));
  private static final Set<String> MESSAGE_KEY_RETURN_METHODS =
      Set.of(
          "VerificationPasswordResetByEmailHandler#getEmailSubjectKey",
          "VerificationPasswordResetByEmailHandler#getCompletionEmailSubjectKey",
          "LoginPasswordResetByEmailHandler#getEmailSubjectKey",
          "LoginPasswordResetByEmailHandler#getCompletionEmailSubjectKey");
  private static final Set<String> MESSAGE_KEY_CARRIERS =
      Set.of(
          "ApiRuntimeException#getErrorCode",
          "ChemistryClientException#getMessageKey",
          "DefaultMessageSourceResolvable#getCode",
          "MediaContentMismatchException#getErrorCode",
          "NfsAuthentication#getMessageForAuthException",
          "ObjectError#getCode",
          "RemovalCircumstancesMessage#key");
  private static final Map<String, String> INDIRECT_MESSAGE_KEYS =
      Map.of(
          "BaseController#getAjaxMessageResponseEntity", "err",
          "ExportableInvRecProperty#getCsvColumnHeader", "messageKey");

  @Test
  void serverJsonKeysAreUniqueAndUseLowerCamelCase() throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    Map<String, List<String>> keyToFiles = new TreeMap<>();
    List<String> invalidKeys = new ArrayList<>();

    try (Stream<Path> files = Files.list(LOCALE_DIRECTORY)) {
      for (Path file :
          files.filter(p -> p.getFileName().toString().startsWith("server.")).toList()) {
        inspect(
            mapper.readTree(file.toFile()),
            "",
            file.getFileName().toString(),
            keyToFiles,
            invalidKeys);
      }
    }

    List<String> collisions =
        keyToFiles.entrySet().stream()
            .filter(entry -> entry.getValue().size() > 1)
            .map(entry -> entry.getKey() + ": " + entry.getValue())
            .toList();
    assertAll(
        () ->
            assertEquals(
                List.of(), collisions, "Dotted key defined in more than one server.*.json file"),
        () ->
            assertEquals(
                List.of(), invalidKeys, "Server JSON property names must use lower camel case"));
  }

  @Test
  void staticallyKnownJavaMessageKeysExist() throws IOException {
    List<Path> paths;
    try (Stream<Path> files = Files.walk(JAVA_SOURCE_DIRECTORY)) {
      paths = files.filter(path -> path.toString().endsWith(".java")).toList();
    }

    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    try (StandardJavaFileManager files = compiler.getStandardFileManager(null, null, null)) {
      assertEquals(
          List.of(),
          missingKeys(scan(compiler, files, files.getJavaFileObjectsFromPaths(paths))),
          "Statically known Java i18n keys must resolve");
    }
  }

  @Test
  void sourceScannerRejectsInvalidSupportedReference() throws IOException {
    JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    JavaFileObject source =
        new StringJavaFileObject(
            "Broken.java",
            """
            @Constraint
            @interface BrokenConstraint {
              String message() default "{test.missing.annotationDefault}";
            }
            class Broken {
              static final String KEY = "test." + "missing.translation";
              MessageSourceUtils messages;
              String message(String key) { return key; }
              boolean flag;
              String suffix;
              void render() {
                messages.getMessage(KEY);
                message("test.missing.wrapper");
                messages.getMessage("test.required");
                messages.getMessage("test." + suffix);
                String dynamic = "test." + suffix;
                messages.getMessage(dynamic);
                messages.getMessage(flag ? "test.missing.true" : "test.missing.false");
              }
            }
            """);

    assertEquals(
        List.of(
            "Broken.java:3: test.missing.annotationDefault",
            "Broken.java:12: test.missing.translation",
            "Broken.java:13: test.missing.wrapper",
            "Broken.java:14: test.required",
            "Broken.java:15: \"test.\" + suffix",
            "Broken.java:17: dynamic",
            "Broken.java:18: test.missing.false",
            "Broken.java:18: test.missing.true"),
        missingKeys(scan(compiler, null, List.of(source))));
  }

  private List<String> missingKeys(List<KeyReference> references) throws IOException {
    JsonMessageSource messages = productionMessageSource();

    return references.stream()
        .filter(
            reference -> {
              if (reference.unresolved()) {
                return true;
              }
              try {
                messages.getMessage(reference.key(), null, ENGLISH);
                return false;
              } catch (NoSuchMessageException e) {
                return true;
              }
            })
        .sorted(
            Comparator.comparing(KeyReference::source)
                .thenComparingLong(KeyReference::line)
                .thenComparing(KeyReference::key))
        .map(reference -> reference.source() + ":" + reference.line() + ": " + reference.key())
        .toList();
  }

  private JsonMessageSource productionMessageSource() throws IOException {
    Path classpathRoot = LOCALE_DIRECTORY.getParent().getParent().getParent();
    Thread thread = Thread.currentThread();
    ClassLoader originalClassLoader = thread.getContextClassLoader();
    try (URLClassLoader productionResources =
        new URLClassLoader(new java.net.URL[] {classpathRoot.toUri().toURL()}, null)) {
      thread.setContextClassLoader(productionResources);
      return new JsonMessageSource();
    } finally {
      thread.setContextClassLoader(originalClassLoader);
    }
  }

  private List<KeyReference> scan(
      JavaCompiler compiler,
      JavaFileManager fileManager,
      Iterable<? extends JavaFileObject> sources)
      throws IOException {
    JavacTask task =
        (JavacTask) compiler.getTask(null, fileManager, null, List.of("-proc:none"), null, sources);
    List<CompilationUnitTree> units = new ArrayList<>();
    task.parse().forEach(units::add);

    SourcePositions positions = Trees.instance(task).getSourcePositions();
    Set<String> customConstraints = new HashSet<>();
    for (CompilationUnitTree unit : units) {
      new TreeScanner<Void, Void>() {
        @Override
        public Void visitClass(ClassTree type, Void unused) {
          if (type.getKind() == Tree.Kind.ANNOTATION_TYPE
              && type.getModifiers().getAnnotations().stream()
                  .anyMatch(
                      annotation ->
                          simpleType(annotation.getAnnotationType().toString())
                              .equals("Constraint"))) {
            customConstraints.add(type.getSimpleName().toString());
          }
          return super.visitClass(type, unused);
        }
      }.scan(unit, null);
    }
    List<KeyReference> references = new ArrayList<>();
    for (CompilationUnitTree unit : units) {
      new SourceScanner(unit, positions, references, customConstraints).scan(unit, null);
    }
    return references;
  }

  private final class SourceScanner extends TreeScanner<Void, Void> {
    private final CompilationUnitTree unit;
    private final SourcePositions positions;
    private final List<KeyReference> references;
    private final Set<String> customConstraints;
    private final Map<String, String> imports = new HashMap<>();
    private final Set<String> wildcardImports = new HashSet<>();
    private final Deque<Scope> scopes = new ArrayDeque<>();
    private final Deque<ClassTree> classes = new ArrayDeque<>();
    private final Deque<MethodTree> methods = new ArrayDeque<>();

    private SourceScanner(
        CompilationUnitTree unit,
        SourcePositions positions,
        List<KeyReference> references,
        Set<String> customConstraints) {
      this.unit = unit;
      this.positions = positions;
      this.references = references;
      this.customConstraints = customConstraints;
      for (ImportTree importTree : unit.getImports()) {
        if (importTree.isStatic()) {
          continue;
        }
        String imported = importTree.getQualifiedIdentifier().toString();
        if (imported.endsWith(".*")) {
          wildcardImports.add(imported.substring(0, imported.length() - 2));
        } else {
          imports.put(simpleType(imported), imported);
        }
      }
    }

    @Override
    public Void visitClass(ClassTree type, Void unused) {
      Scope classScope = new Scope(true);
      for (Tree member : type.getMembers()) {
        if (member instanceof VariableTree variable) {
          addVariable(classScope, variable, -1);
        }
      }
      scopes.push(classScope);
      classes.push(type);
      try {
        return super.visitClass(type, unused);
      } finally {
        classes.pop();
        scopes.pop();
      }
    }

    @Override
    public Void visitMethod(MethodTree method, Void unused) {
      Scope methodScope = new Scope(false);
      for (int i = 0; i < method.getParameters().size(); i++) {
        addVariable(methodScope, method.getParameters().get(i), i);
      }
      scopes.push(methodScope);
      methods.push(method);
      try {
        if (!classes.isEmpty()
            && classes.peek().getKind() == Tree.Kind.ANNOTATION_TYPE
            && customConstraints.contains(currentClassName())
            && method.getName().contentEquals("message")
            && method.getDefaultValue() instanceof ExpressionTree defaultValue) {
          addValidationReference(defaultValue, method);
        }
        return super.visitMethod(method, unused);
      } finally {
        methods.pop();
        scopes.pop();
      }
    }

    @Override
    public Void visitBlock(BlockTree block, Void unused) {
      return inLocalScope(() -> super.visitBlock(block, unused));
    }

    @Override
    public Void visitForLoop(ForLoopTree loop, Void unused) {
      return inLocalScope(() -> super.visitForLoop(loop, unused));
    }

    @Override
    public Void visitEnhancedForLoop(EnhancedForLoopTree loop, Void unused) {
      return inLocalScope(() -> super.visitEnhancedForLoop(loop, unused));
    }

    @Override
    public Void visitCatch(CatchTree catchTree, Void unused) {
      return inLocalScope(() -> super.visitCatch(catchTree, unused));
    }

    @Override
    public Void visitLambdaExpression(LambdaExpressionTree lambda, Void unused) {
      return inLocalScope(() -> super.visitLambdaExpression(lambda, unused));
    }

    @Override
    public Void visitVariable(VariableTree variable, Void unused) {
      if (!scopes.isEmpty()) {
        addVariable(scopes.peek(), variable, -1);
      }
      return super.visitVariable(variable, unused);
    }

    @Override
    public Void visitMethodInvocation(MethodInvocationTree call, Void unused) {
      Integer argument = boundaryArgument(call);
      if (argument != null && call.getArguments().size() > argument) {
        addReference(call.getArguments().get(argument), call);
      }
      return super.visitMethodInvocation(call, unused);
    }

    @Override
    public Void visitReturn(ReturnTree returnTree, Void unused) {
      if (returnTree.getExpression() != null
          && !methods.isEmpty()
          && MESSAGE_KEY_RETURN_METHODS.contains(
              currentClassName() + "#" + methods.peek().getName())) {
        addReference(returnTree.getExpression(), returnTree);
      }
      return super.visitReturn(returnTree, unused);
    }

    @Override
    public Void visitNewClass(NewClassTree constructor, Void unused) {
      String type = simpleType(constructor.getIdentifier().toString());
      if (!constructor.getArguments().isEmpty()
          && (MESSAGE_CONSTRUCTOR_TYPES.contains(type)
              || type.equals("DefaultMessageSourceResolvable"))) {
        addReference(constructor.getArguments().get(0), constructor);
      }
      return super.visitNewClass(constructor, unused);
    }

    @Override
    public Void visitAnnotation(AnnotationTree annotation, Void unused) {
      String annotationType = qualifiedAnnotationType(annotation.getAnnotationType().toString());
      if (!isValidationConstraint(annotationType)) {
        return super.visitAnnotation(annotation, unused);
      }
      boolean explicitMessage = false;
      for (ExpressionTree argument : annotation.getArguments()) {
        if (argument instanceof AssignmentTree assignment
            && assignment.getVariable().toString().equals("message")) {
          explicitMessage = true;
          addValidationReference(assignment.getExpression(), annotation);
        }
      }
      if (!explicitMessage && isStandardValidationConstraint(annotationType)) {
        add(annotationType + ".message", annotation, false);
      }
      return super.visitAnnotation(annotation, unused);
    }

    private Void inLocalScope(java.util.function.Supplier<Void> scan) {
      scopes.push(new Scope(false));
      try {
        return scan.get();
      } finally {
        scopes.pop();
      }
    }

    private void addVariable(Scope scope, VariableTree variable, int parameterIndex) {
      String name = variable.getName().toString();
      VariableInfo existing = scope.variables.get(name);
      if (parameterIndex < 0 && existing != null && existing.parameterIndex() >= 0) {
        return;
      }
      scope.variables.put(
          name,
          new VariableInfo(
              variable.getType() == null ? null : simpleType(variable.getType().toString()),
              variable.getInitializer(),
              variable.getModifiers().getFlags().contains(Modifier.FINAL),
              parameterIndex));
    }

    private Integer boundaryArgument(MethodInvocationTree call) {
      String method;
      String receiver = null;
      if (call.getMethodSelect() instanceof IdentifierTree identifier) {
        method = identifier.getName().toString();
      } else if (call.getMethodSelect() instanceof MemberSelectTree member) {
        method = member.getIdentifier().toString();
        receiver = member.getExpression().toString();
      } else {
        return null;
      }

      String receiverType = receiverType(receiver);
      String owner = receiverType != null ? receiverType : currentClassName();
      Integer customArgument = MESSAGE_KEY_ARGUMENTS.get(owner + "#" + method);
      if (customArgument != null) {
        return customArgument;
      }
      if (method.equals("rejectValue")
          && ("BindingResult".equals(receiverType) || "Errors".equals(receiverType))) {
        return 1;
      }
      if (method.equals("reject")
          && ("BindingResult".equals(receiverType) || "Errors".equals(receiverType))) {
        return 0;
      }
      if ((receiver == null && Set.of("getMessage", "message").contains(method))
          || (receiverType != null
              && MESSAGE_PROVIDER_TYPES.contains(receiverType)
              && Set.of("getMessage", "getMessageForLocale", "format").contains(method))) {
        return 0;
      }
      if (method.equals("getText") && receiver == null) {
        return 0;
      }
      if (method.equals("render") && "EmailContentGenerator".equals(receiverType)) {
        return 0;
      }
      return null;
    }

    private String receiverType(String receiver) {
      if (receiver == null) {
        return null;
      }
      String receiverName = receiver.substring(receiver.lastIndexOf('.') + 1);
      VariableInfo variable =
          receiver.startsWith("this.")
              ? findVariable(receiverName, true)
              : findVariable(receiverName, false);
      return variable == null ? null : variable.type();
    }

    private String currentClassName() {
      if (classes.isEmpty()) {
        return "";
      }
      String name = classes.peek().getSimpleName().toString();
      if (!name.isEmpty()) {
        return name;
      }
      return classes.stream()
          .map(type -> type.getSimpleName().toString())
          .filter(candidate -> !candidate.isEmpty())
          .findFirst()
          .orElse("");
    }

    private void addReference(ExpressionTree expression, Tree site) {
      Set<String> keys = constantValues(expression, new HashSet<>());
      if (keys != null) {
        keys.forEach(key -> add(key, site, false));
      } else if (!isForwardedKeyParameter(expression)
          && !isMessageObject(expression)
          && !isKnownKeyCarrier(expression)
          && !isKnownIndirectKey(expression)) {
        add(expression.toString(), site, true);
      }
    }

    private boolean isForwardedKeyParameter(ExpressionTree expression) {
      if (!(expression instanceof IdentifierTree identifier) || methods.isEmpty()) {
        return false;
      }
      VariableInfo variable = findVariable(identifier.getName().toString(), false);
      if (variable == null || variable.parameterIndex() < 0) {
        return false;
      }
      MethodTree method = methods.peek();
      Integer declaredBoundary =
          MESSAGE_KEY_ARGUMENTS.get(currentClassName() + "#" + method.getName());
      if (declaredBoundary == null
          && Set.of("getMessage", "getMessageForLocale", "getText", "message", "format", "render")
              .contains(method.getName().toString())) {
        declaredBoundary = 0;
      }
      return declaredBoundary != null && declaredBoundary == variable.parameterIndex();
    }

    private boolean isMessageObject(ExpressionTree expression) {
      if (!(expression instanceof IdentifierTree identifier)) {
        return false;
      }
      VariableInfo variable = findVariable(identifier.getName().toString(), false);
      return variable != null && variable.type() != null && !variable.type().equals("String");
    }

    private boolean isKnownKeyCarrier(ExpressionTree expression) {
      if (!(expression instanceof MethodInvocationTree invocation)) {
        return false;
      }
      if (invocation.getMethodSelect() instanceof MemberSelectTree member) {
        if (member.getIdentifier().contentEquals("getLabelKey")) {
          return true;
        }
        String carrier = receiverType(member.getExpression().toString());
        if (carrier != null
            && MESSAGE_KEY_CARRIERS.contains(carrier + "#" + member.getIdentifier())) {
          return true;
        }
        return member.getIdentifier().contentEquals("getMessageKey")
            && currentMethod().equals("ApiControllerAdvice#handleAuth");
      }
      String method = invocation.getMethodSelect().toString();
      return currentClassName().equals("PasswordResetByEmailHandlerBase")
          && Set.of("getEmailSubjectKey", "getCompletionEmailSubjectKey").contains(method);
    }

    private boolean isKnownIndirectKey(ExpressionTree expression) {
      return expression.toString().equals(INDIRECT_MESSAGE_KEYS.get(currentMethod()));
    }

    private String currentMethod() {
      return methods.isEmpty() ? "" : currentClassName() + "#" + methods.peek().getName();
    }

    private Set<String> constantValues(ExpressionTree expression, Set<ExpressionTree> resolving) {
      if (expression instanceof LiteralTree literal && literal.getValue() instanceof String value) {
        return Set.of(value);
      }
      if (expression instanceof ParenthesizedTree parenthesized) {
        return constantValues(parenthesized.getExpression(), resolving);
      }
      if (expression instanceof ConditionalExpressionTree conditional) {
        Set<String> whenTrue = constantValues(conditional.getTrueExpression(), resolving);
        Set<String> whenFalse = constantValues(conditional.getFalseExpression(), resolving);
        if (whenTrue == null || whenFalse == null) {
          return null;
        }
        Set<String> values = new LinkedHashSet<>(whenTrue);
        values.addAll(whenFalse);
        return values;
      }
      if (expression instanceof BinaryTree binary && binary.getKind() == Tree.Kind.PLUS) {
        Set<String> left = constantValues(binary.getLeftOperand(), resolving);
        Set<String> right = constantValues(binary.getRightOperand(), resolving);
        if (left == null || right == null) {
          return null;
        }
        Set<String> values = new LinkedHashSet<>();
        left.forEach(leftValue -> right.forEach(rightValue -> values.add(leftValue + rightValue)));
        return values;
      }

      VariableInfo variable = null;
      if (expression instanceof IdentifierTree identifier) {
        variable = findVariable(identifier.getName().toString(), false);
      } else if (expression instanceof MemberSelectTree member
          && (member.getExpression().toString().equals("this")
              || member.getExpression().toString().equals(currentClassName()))) {
        variable = findVariable(member.getIdentifier().toString(), true);
      }
      if (variable == null
          || !variable.isFinal()
          || variable.initializer() == null
          || !resolving.add(variable.initializer())) {
        return null;
      }
      Set<String> values = constantValues(variable.initializer(), resolving);
      resolving.remove(variable.initializer());
      return values;
    }

    private VariableInfo findVariable(String name, boolean classOnly) {
      for (Scope scope : scopes) {
        if ((!classOnly || scope.classScope) && scope.variables.containsKey(name)) {
          return scope.variables.get(name);
        }
      }
      return null;
    }

    private void addValidationReference(ExpressionTree expression, Tree site) {
      Set<String> messages = constantValues(expression, new HashSet<>());
      if (messages == null) {
        add(expression.toString(), site, true);
        return;
      }
      for (String message : messages) {
        Matcher matcher = VALIDATION_KEY.matcher(message);
        if (matcher.matches()) {
          add(matcher.group(1), site, false);
        } else {
          add(message, site, true);
        }
      }
    }

    private String qualifiedAnnotationType(String type) {
      if (type.contains(".")) {
        return type;
      }
      String explicit = imports.get(type);
      if (explicit != null) {
        return explicit;
      }
      for (String wildcard : wildcardImports) {
        if (wildcard.equals("jakarta.validation.constraints")
            || wildcard.equals("org.hibernate.validator.constraints")) {
          return wildcard + "." + type;
        }
      }
      return type;
    }

    private boolean isStandardValidationConstraint(String type) {
      return type.startsWith("jakarta.validation.constraints.")
          || type.startsWith("org.hibernate.validator.constraints.");
    }

    private boolean isValidationConstraint(String type) {
      return isStandardValidationConstraint(type) || customConstraints.contains(simpleType(type));
    }

    private void add(String key, Tree site, boolean unresolved) {
      long position = positions.getStartPosition(unit, site);
      long line = position < 0 ? -1 : unit.getLineMap().getLineNumber(position);
      references.add(new KeyReference(key, sourceName(unit.getSourceFile()), line, unresolved));
    }
  }

  private static String simpleType(String type) {
    int generic = type.indexOf('<');
    String withoutGeneric = generic < 0 ? type : type.substring(0, generic);
    return withoutGeneric.substring(withoutGeneric.lastIndexOf('.') + 1);
  }

  private static String sourceName(JavaFileObject source) {
    try {
      return Path.of("")
          .toAbsolutePath()
          .relativize(Path.of(source.toUri()).toAbsolutePath())
          .toString();
    } catch (Exception e) {
      String name = source.getName();
      return name.startsWith("/") ? name.substring(1) : name;
    }
  }

  private void inspect(
      JsonNode node,
      String prefix,
      String filename,
      Map<String, List<String>> keyToFiles,
      List<String> invalidKeys) {
    if (!node.isObject()) {
      if (node.isValueNode() && !prefix.isEmpty()) {
        keyToFiles.computeIfAbsent(prefix, key -> new ArrayList<>()).add(filename);
      }
      return;
    }

    Iterator<Entry<String, JsonNode>> fields = node.fields();
    while (fields.hasNext()) {
      Entry<String, JsonNode> field = fields.next();
      String key = prefix.isEmpty() ? field.getKey() : prefix + "." + field.getKey();
      boolean standardConstraintType =
          !field.getKey().isEmpty()
              && (key.startsWith("jakarta.validation.constraints.")
                  || key.startsWith("org.hibernate.validator.constraints."))
              && Character.isUpperCase(field.getKey().charAt(0));
      if ((!LOWER_CAMEL_CASE_KEY_COMPONENT.matcher(field.getKey()).matches()
              && !standardConstraintType)
          || LOWERCASE_FIELD_SUFFIX.matcher(field.getKey()).matches()) {
        invalidKeys.add(key);
      }
      inspect(field.getValue(), key, filename, keyToFiles, invalidKeys);
    }
  }

  private static final class Scope {
    private final boolean classScope;
    private final Map<String, VariableInfo> variables = new HashMap<>();

    private Scope(boolean classScope) {
      this.classScope = classScope;
    }
  }

  private record VariableInfo(
      String type, ExpressionTree initializer, boolean isFinal, int parameterIndex) {}

  private record KeyReference(String key, String source, long line, boolean unresolved) {}

  private static final class StringJavaFileObject extends SimpleJavaFileObject {
    private final String source;

    private StringJavaFileObject(String name, String source) {
      super(URI.create("string:///" + name), Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }
}
