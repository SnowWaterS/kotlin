// Generated by the protocol buffer compiler.  DO NOT EDIT!
// source: compiler/ir/serialization.common/src/KotlinIr.proto

package org.jetbrains.kotlin.backend.common.serialization.proto;

public interface IrBlockOrBuilder extends
    // @@protoc_insertion_point(interface_extends:org.jetbrains.kotlin.backend.common.serialization.proto.IrBlock)
    org.jetbrains.kotlin.protobuf.MessageLiteOrBuilder {

  /**
   * <code>optional .org.jetbrains.kotlin.backend.common.serialization.proto.IrStatementOrigin origin = 1;</code>
   */
  boolean hasOrigin();
  /**
   * <code>optional .org.jetbrains.kotlin.backend.common.serialization.proto.IrStatementOrigin origin = 1;</code>
   */
  org.jetbrains.kotlin.backend.common.serialization.proto.IrStatementOrigin getOrigin();

  /**
   * <code>repeated .org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement statement = 2;</code>
   */
  java.util.List<org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement> 
      getStatementList();
  /**
   * <code>repeated .org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement statement = 2;</code>
   */
  org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement getStatement(int index);
  /**
   * <code>repeated .org.jetbrains.kotlin.backend.common.serialization.proto.IrStatement statement = 2;</code>
   */
  int getStatementCount();
}