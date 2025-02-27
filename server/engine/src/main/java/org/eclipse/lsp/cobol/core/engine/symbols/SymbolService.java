/*
 * Copyright (c) 2022 Broadcom.
 * The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *    Broadcom, Inc. - initial API and implementation
 *
 */
package org.eclipse.lsp.cobol.core.engine.symbols;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.inject.Singleton;
import lombok.Value;
import org.eclipse.lsp.cobol.core.messages.MessageTemplate;
import org.eclipse.lsp.cobol.core.model.ErrorSeverity;
import org.eclipse.lsp.cobol.core.model.ErrorSource;
import org.eclipse.lsp.cobol.core.model.SyntaxError;
import org.eclipse.lsp.cobol.core.model.VariableUsageUtils;
import org.eclipse.lsp.cobol.core.model.tree.*;
import org.eclipse.lsp.cobol.core.model.tree.variables.VariableNode;
import org.eclipse.lsp.cobol.core.model.tree.variables.VariableUsageNode;
import org.eclipse.lsp.cobol.core.preprocessor.delegates.injector.ImplicitCodeUtils;
import org.eclipse.lsp.cobol.service.CobolDocumentModel;
import org.eclipse.lsp.cobol.service.delegates.validations.AnalysisResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.TextDocumentPositionParams;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static org.eclipse.lsp.cobol.core.model.tree.Node.hasType;
import static org.eclipse.lsp.cobol.core.preprocessor.delegates.util.RangeUtils.findNodeByPosition;

/** Service to handle symbol information and dependencies */
@Singleton
public class SymbolService {
  private final Map<String, SymbolTable> programSymbols;

  public SymbolService() {
    this.programSymbols = Collections.synchronizedMap(new HashMap<>());
  }

  public SymbolService(Map<String, SymbolTable> symbolTableMap) {
    this.programSymbols = symbolTableMap;
  }

  /**
   * Add the variable definition to that program context.
   *
   * @param programNode the program where this variable belongs to.
   * @param node the variable definition node
   */
  public void addVariableDefinition(ProgramNode programNode, VariableNode node) {
    createOrGetSymbolTable(programNode).getVariables().put(node.getName(), node);
  }
  /**
   * Find element using a position
   *
   * @param document the document to search in
   * @param position the position to check
   * @return element at specified position
   */
  public Optional<Context> findElementByPosition(
      CobolDocumentModel document, TextDocumentPositionParams position) {
    AnalysisResult result = document.getAnalysisResult();
    if (result.getRootNode() == null) {
      return Optional.empty();
    }
    Optional<Node> node = findNodeByPosition(result.getRootNode(), position.getTextDocument().getUri(), position.getPosition());
    return node.filter(Context.class::isInstance)
            .map(Context.class::cast)
            .map(this::constructElementsExcludingImplicits);
  }

  private Context constructElementsExcludingImplicits(Context ctx) {
    List<Location> definitions =
        ctx.getDefinitions().stream().filter(uriNotImplicit()).collect(Collectors.toList());
    List<Location> usages =
        ctx.getUsages().stream().filter(uriNotImplicit()).collect(Collectors.toList());
    return new Element("", definitions, usages);
  }

  private static Predicate<Location> uriNotImplicit() {
    return i -> !ImplicitCodeUtils.isImplicit(i.getUri());
  }

  /**
   * * Register variable definitions into nearest ProgramNode
   *
   * @param node the node with VariableDefinitionNodes
   */
  public void registerVariablesInProgram(Node node) {
    // The variable can have nested variable definitions (like IndexItemNode), we need to
    // collect them
    List<VariableNode> variables =
        node.getChildren().stream()
            .flatMap(Node::getDepthFirstStream)
            .filter(hasType(NodeType.VARIABLE))
            .map(VariableNode.class::cast)
            .collect(Collectors.toList());
    node.getProgram()
        .ifPresent(programNode -> variables.forEach(v -> addVariableDefinition(programNode, v)));
  }

  /**
   * Add a paragraph defined in the program context.
   *
   * @param program - the program to register code block in
   * @param node - the paragraph node
   */
  public void registerCodeBlock(ProgramNode program, CodeBlockDefinitionNode node) {
    SymbolTable symbolTable = createOrGetSymbolTable(program);
    symbolTable.getCodeBlocks().add(node);
  }

  /**
   * Add the usage of a code block defined in this program. Returns an optional syntax error if the
   * paragraph is not defined.
   *
   * @param program the program to register block usage in
   * @param node the usage node to register
   * @return Optional error if the paragraph or section with the given name is not defined
   */
  public Optional<SyntaxError> registerCodeBlockUsage(
      ProgramNode program, CodeBlockUsageNode node) {
    SymbolTable symbolTable = createOrGetSymbolTable(program);

    List<CodeBlockDefinitionNode> definitions =
        symbolTable.getCodeBlocks().stream()
            .filter(it -> filterNodes(it, node))
            .collect(Collectors.toList());

    if (definitions.size() == 0) {
      return Optional.of(
          SyntaxError.syntaxError()
              .errorSource(ErrorSource.PARSING)
              .messageTemplate(
                  MessageTemplate.of("semantics.paragraphNotDefined", node.getName()))
              .severity(ErrorSeverity.ERROR)
              .locality(node.getLocality())
              .build());
    }

    if (definitions.size() > 1) {
      // Try to resolve ambiguous reference.
      // If GO TO is in the same section as a paragraph - no errors
      String usageSectionName = getSectionName(node);

      definitions = definitions.stream()
          .filter(d -> getSectionName(d).equalsIgnoreCase(usageSectionName))
          .collect(Collectors.toList());

      if (definitions.size() > 1) {
        return Optional.of(
            SyntaxError.syntaxError()
                .errorSource(ErrorSource.PARSING)
                .messageTemplate(
                    MessageTemplate.of("semantics.ambiguous", node.getName()))
                .severity(ErrorSeverity.ERROR)
                .locality(node.getLocality())
                .build());
      }
    }

    CodeBlockDefinitionNode definition = definitions.get(0);
    definition.addUsage(node.getLocality());

    Optional.ofNullable(symbolTable.getParagraphMap().get(node.getName()))
        .ifPresent(it -> it.addUsage(node.getLocality().toLocation()));
    Optional.ofNullable(symbolTable.getSectionMap().get(node.getName()))
        .ifPresent(it -> it.addUsage(node.getLocality().toLocation()));

    return Optional.empty();
  }

  private boolean filterNodes(CodeBlockDefinitionNode definition, CodeBlockUsageNode usage) {
    if (!usage.getName().equalsIgnoreCase(definition.getName())) {
      return false;
    }

    //Filter nodes in case of section usage in the PERFORM or GO TO statements. i.e. GO TO PARAG1 OF SECTION-1.
    if (usage.getParent().getNodeType() == NodeType.PERFORM
        || usage.getParent().getNodeType() == NodeType.GO_TO
        || usage.getParent().getNodeType() == NodeType.SENTENCE) {
      Optional<SectionNameNode> sectionNameNode = usage.getParent().getChildren().stream()
          .filter(c -> c instanceof SectionNameNode)
          .map(SectionNameNode.class::cast).findFirst();

      return sectionNameNode
          .map(SectionNameNode::getName)
          .map(n -> n.equalsIgnoreCase(getSectionName(definition)))
          .orElse(true);
    }
    return true;
  }

  private String getSectionName(Node node) {
    Node parent = node.getParent();
    while (parent != null) {
      if (parent instanceof ProcedureSectionNode) {
        return ((ProcedureSectionNode) parent).getName();
      }
      node = parent;
      parent = node.getParent();
    }
    return "";
  }


  private SymbolTable createOrGetSymbolTable(ProgramNode program) {
    return programSymbols.computeIfAbsent(
        program.getProgramName() + "%" + program.getLocality().getUri(), p -> new SymbolTable());
  }

  /**
   * Add a section definition name node in the program context.
   *
   * @param program the program to register section in
   * @param node - the section definition node
   * @return syntax error if the code block duplicates
   */
  public Optional<SyntaxError> registerSectionNameNode(ProgramNode program, SectionNameNode node) {
    createOrGetSymbolTable(program)
        .getSectionMap()
        .computeIfAbsent(node.getName(), n -> new CodeBlockReference())
        .addDefinition(node.getLocality().toLocation());
    return Optional.empty();
  }

  /**
   * Add a paragraph definition name node in the program context.
   *
   * @param programNode the program to register in
   * @param node - the section definition node
   * @return syntax error if the code block duplicates
   */
  public Optional<SyntaxError> registerParagraphNameNode(
      ProgramNode programNode, ParagraphNameNode node) {
    createOrGetSymbolTable(programNode)
        .getParagraphMap()
        .computeIfAbsent(node.getName(), n -> new CodeBlockReference())
        .addDefinition(node.getLocality().toLocation());
    return Optional.empty();
  }

  /**
   * Search for a block reference in a paragraph and then in a section map
   *
   * @param programNode the program to search block references in
   * @param name the name of the block
   * @return the block reference or null if not found
   */
  public CodeBlockReference getCodeBlockReference(ProgramNode programNode, String name) {
    SymbolTable symbolTable = createOrGetSymbolTable(programNode);
    return symbolTable.getParagraphMap().computeIfAbsent(name, symbolTable.getSectionMap()::get);
  }

  /**
   * Get Section locations
   *
   * @param node the section node
   * @param retrieveLocations location extract function
   * @return a list of locations
   */
  public List<Location> getSectionLocations(
      SectionNameNode node, Function<CodeBlockReference, List<Location>> retrieveLocations) {
    return node.getProgram()
        .map(this::createOrGetSymbolTable)
        .map(SymbolTable::getSectionMap)
        .map(it -> it.get(node.getName()))
        .map(retrieveLocations)
        .orElse(ImmutableList.of());
  }

  /**
   * Get paragraphs data
   *
   * @param programNode the program node
   * @return map of paragraphs
   */
  public Map<String, CodeBlockReference> getParagraphMap(ProgramNode programNode) {
    return createOrGetSymbolTable(programNode).getParagraphMap();
  }
  /**
   * Get section data
   *
   * @param programNode the program node
   * @return map of sections
   */
  public Map<String, CodeBlockReference> getSectionMap(ProgramNode programNode) {
    return createOrGetSymbolTable(programNode).getSectionMap();
  }

  /**
   * Extract all accumulated symbols information
   *
   * @return Symbol Tables
   */
  public Map<String, SymbolTable> getProgramSymbols() {
    return programSymbols;
  }

  /**
   * Get variable definition node based on list of variable usage nodes.
   *
   * @param programNode the program node
   * @param usageNodes represents variable name and its parents
   * @return the list of founded variable definitions
   */
  public List<VariableNode> getVariableDefinition(
      ProgramNode programNode, List<VariableUsageNode> usageNodes) {
    Multimap<String, VariableNode> variables = createOrGetSymbolTable(programNode).getVariables();
    List<VariableNode> foundDefinitions =
        VariableUsageUtils.findVariablesForUsage(variables, usageNodes);
    if (!foundDefinitions.isEmpty()) {
      return foundDefinitions;
    }

    Multimap<String, VariableNode> globals = ArrayListMultimap.create();
    getMapOfGlobalVariables(programNode)
        .values()
        .forEach(variableNode -> globals.put(variableNode.getName(), variableNode));
    return VariableUsageUtils.findVariablesForUsage(globals, usageNodes);
  }

  private Map<String, VariableNode> getMapOfGlobalVariables(ProgramNode programNode) {
    Map<String, VariableNode> result =
        programNode.getProgram().map(this::getMapOfGlobalVariables).orElseGet(HashMap::new);
    createOrGetSymbolTable(programNode).getVariables().values().stream()
        .filter(VariableNode::isGlobal)
        .forEach(variableNode -> result.put(variableNode.getName(), variableNode));
    return result;
  }

  /**
   * Get variables data
   *
   * @param programNode the program node
   * @return map of variables
   */
  public Multimap<String, VariableNode> getVariables(ProgramNode programNode) {
    return createOrGetSymbolTable(programNode).getVariables();
  }

  /**
   * Remove program related symbols
   *
   * @param documentUri the program uri
   */
  public void reset(String documentUri) {
    programSymbols.keySet().stream()
        .filter(k -> k.endsWith("%" + documentUri))
        .collect(Collectors.toList())
        .forEach(programSymbols::remove);
    programSymbols.remove(documentUri);
  }

  @Value
  private static class Element implements Context {
    String name;
    List<Location> definitions;
    List<Location> usages;
  }
}
