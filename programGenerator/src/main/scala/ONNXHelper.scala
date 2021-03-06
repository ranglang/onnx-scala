/*
 * ONNXHelper
 * Copyright (c) 2018 Alexander Merritt
 * All rights reserved.
 * This program is free software: you can redistribute it and/or modify
 *
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.emergentorder.onnx

import java.nio.file._
import java.nio.ByteBuffer
import collection.JavaConverters._
import scala.reflect.ClassTag

import org.bytedeco.javacpp._
import org.bytedeco.onnx._
import org.bytedeco.onnx.global.onnx._

class ONNXHelper(modelFileName: String) {

  //TODO: Add the rest of the types
  type ValidTensorProtoTypes = Array[Float]

//  org.bytedeco.javacpp.Loader.load(classOf[org.bytedeco.onnx])

  val byteArray = Files.readAllBytes(Paths.get(modelFileName))

  val res = new ModelProto()
  ParseProtoFromBytes(res.asInstanceOf[MessageLite],
                      new BytePointer(byteArray: _*),
                      byteArray.length.toLong)
  val graph = res.graph

  def maxOpsetVersion =
    try {
      res.opset_import(0).version
    } catch {
      case e: Exception => { 1 }
    }

//  println("max opset : " + maxOpsetVersion)

  def dimsToArray[VV: spire.math.Numeric: ClassTag](
      dimsCount: Int,
      dimsList: List[Long]): Array[VV] = {
    val dimsArrayInt = dimsList.map(x => x.toInt).toArray
    val arrX = dimsCount match {
      case 1 => Array.ofDim[VV](dimsArrayInt(0))
      case 2 => Array.ofDim[VV](dimsArrayInt(0) * dimsArrayInt(1))
      case 3 =>
        Array
          .ofDim[VV](dimsArrayInt(0) * dimsArrayInt(1) * dimsArrayInt(2))
      case 4 =>
        Array
          .ofDim[VV](
            dimsArrayInt(0) *
              dimsArrayInt(1) *
              dimsArrayInt(2) *
              dimsArrayInt(3))
      case 5 =>
        Array
          .ofDim[VV](
            dimsArrayInt(0) *
              dimsArrayInt(1) *
              dimsArrayInt(2) *
              dimsArrayInt(3) *
              dimsArrayInt(4))
    }
    arrX
  }

  def onnxTensorProtoToArray(
      tensorProto: TensorProto): ValidTensorProtoTypes = {

    val onnxDataType = tensorProto.data_type
    val dimsCount = tensorProto.dims_size
    val dimsList =
      (0 until dimsCount.toInt).map(x => tensorProto.dims(x)).toList

    val bytesBuffer = tensorProto.raw_data.asByteBuffer

    val TensProtoInt = TensorProto.INT32

    val TensProtoFloat = TensorProto.FLOAT

    val array: ValidTensorProtoTypes = onnxDataType match {
//      case TensProtoInt => {
//        val arrX = dimsToArray[Int](dimsCount, dimsList)
//        bytesBuffer.asIntBuffer.get(arrX)
//        arrX.toArray
//        //arrX.map(x => x.asInstanceOf[VV])
//      }
      case TensProtoFloat => {
        val arrX = dimsToArray[Float](dimsCount, dimsList)
        bytesBuffer.asFloatBuffer.get(arrX)
        arrX.toArray
        //arrX.map(y => if(y.isNaN) 0.0f else y).map(x => x.asInstanceOf[VV])
      }
    }
    array
  }

  val nodeCount = graph.node_size.toInt
  val node = (0 until nodeCount).map(x => graph.node(x)).toList

  def attributes =
    node.map { x =>
      val attributeCount = x.attribute_size.toInt
      val attribute = (0 until attributeCount).map(y => x.attribute(y)).toArray
      attribute
    }.toArray

  def ops = node.map(x => x.op_type.getString).toArray

  def nodeInputs =
    node
      .map { x =>
        val inputCount = x.input_size.toInt
        val input = (0 until inputCount).map(y => x.input(y)).toList

        input
      }
      .toArray
      .map { x =>
        x.toArray
          .map(
            y =>
              y.getString
                .asInstanceOf[String]
                .replaceAll("-", "_")
                .replaceAll("/", "_"))
      //.filter(x =>
      //  nodeNames.contains("input_" + x) || nodeNames
      //   .contains("param_" + x) || nodeNames.contains("output_" + x))
      }

  def nodeOutputs =
    node
      .map { x =>
        val outputCount = x.output_size.toInt
        val output = (0 until outputCount).map(y => x.output(y)).toList

        output
      }
      .toArray
      .map { x =>
        x.toArray.map(
          y =>
            y.getString
              .asInstanceOf[String]
              .replaceAll("-", "_")
              .replaceAll("/", "_"))
      }

  val globalOutputCount = graph.output_size.toInt
  val globalOutput =
    (0 until globalOutputCount).map(x => graph.output(x)).toList

  def outputs = {
    val outputArray = globalOutput.toArray
    outputArray
      .map(x => x.name.getString.replaceAll("-", "_").replaceAll("/", "_"))
      .filter(x => nodeNames.contains("output_" + x))
  }

  def nodeNames = nodes.map(y => y) // y._1

  val inputCount = graph.input_size.toInt
  val input = (0 until inputCount).map(x => graph.input(x)).toList

  def nodes = {
    val someNodes = input.map { x =>
      val name = x.name.getString
      if (params exists (_.equals(name)))
        ("param_" + name) //, params.get(name).map(y => y._2)) //TODO: Separate getting the param arrays
      else ("input_" + name) //, params.get(name).map(y => y._2))
    } ++ nodeOutputs.flatten.map(y => ("output_" + y)) //,None))
    someNodes
  }

  val initializerCount = graph.initializer_size
  val initializer =
    (0 until initializerCount).map(x => graph.initializer(x)).toList

  def params =
    initializer.map { x =>
      val dimsCount = x.dims_size
      val dimsList = (0 until dimsCount.toInt).map(y => x.dims(y)).toList
      //def arrX: ValidTensorProtoTypes = onnxTensorProtoToArray(x)
      x.name.getString.replaceAll("-", "_").replaceAll("/", "_")
    }
  //-> (arrX, dimsList)
  // }.toMap

}
