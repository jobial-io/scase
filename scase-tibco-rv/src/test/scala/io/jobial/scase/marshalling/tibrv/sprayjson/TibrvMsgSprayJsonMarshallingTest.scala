package io.jobial.scase.marshalling.tibrv.sprayjson

import com.tibco.tibrv.TibrvDate
import com.tibco.tibrv.TibrvMsg
import io.jobial.scase.marshalling.Marshaller
import io.jobial.scase.marshalling.Unmarshaller
import org.joda.time.DateTime
import org.joda.time.DateTime.now
import org.joda.time.LocalDate
import org.scalatest.flatspec.AnyFlatSpec

case class Employee(
  name: String,
  id: Int,
  salary: Double,
  children: Option[Int],
  long: Long,
  dateOfBirth: LocalDate,
  active: Boolean,
  nicknames: List[String],
  address: Option[Address] = None,
  timestamp: DateTime
)

case class Address(
  address: String
)

class TibrvMsgSprayJsonMarshallingTest extends AnyFlatSpec with TibrvMsgSprayJsonMarshalling {

  implicit val manidrFormat = jsonFormat1(Address)

  implicit val testPersonFormat = jsonFormat10(Employee)

  "marshalling" should "work" in {
    val timestamp = now
    val dateOfBirth = LocalDate.now
    val m = new TibrvMsg(Marshaller[Employee].marshal(
      Employee("X", 111, 30000.0, Some(1), Long.MaxValue, dateOfBirth, true, List("a", "b"), Some(Address("Y")), timestamp)
    ))
    println(m)
    assert(m.getInt("id", 0) === 111)
    assert(m.get("name").toString === "X")
    assert(m.getInt("children", 0) === 1)
    assert(m.getField("salary").data.asInstanceOf[Double] === 30000.0)
    assert(m.getField("timestamp").data.asInstanceOf[TibrvDate] === new TibrvDate(timestamp.toDate))
    assert(m.getField("dateOfBirth").data.asInstanceOf[TibrvDate] === new TibrvDate(dateOfBirth.toDate))
    assert(m.getField("long").data.asInstanceOf[java.lang.Long] === Long.MaxValue)
    assert(m.getField("active").data.asInstanceOf[java.lang.Boolean] === true)
    assert(m.getField("nicknames").data.asInstanceOf[TibrvMsg].getNumFields === 3)
    assert(m.getField("address").data.asInstanceOf[TibrvMsg].get("address").toString === "Y")
  }

  "unmarshalling" should "work" in {
    val dateOfBirth = LocalDate.now
    val timestamp = now
    val m = new TibrvMsg
    m.add("id", 137)
    m.add("name", "X")
    m.add("salary", 50000.0)
    m.add("dateOfBirth", dateOfBirth.toDate)
    m.add("timestamp", timestamp.toDate)
    m.add("long", Long.MaxValue)
    m.add("active", true)
    val nicknames = new TibrvMsg
    nicknames.add("$type", "JsArray")
    nicknames.add(null, "a")
    nicknames.add(null, "b")
    m.add("nicknames", nicknames)
    val address = new TibrvMsg
    address.add("address", "Z")
    m.add("address", address)
    val p = Unmarshaller[Employee].unmarshal(m.getAsBytes).right.get
    println(p)
    assert(p.id === 137)
    assert(p.name === "X")
    assert(p.children === None)
    assert(p.salary === 50000.0)
    assert(p.dateOfBirth === dateOfBirth)
    assert(p.timestamp === timestamp)
    assert(p.long === Long.MaxValue)
    assert(p.active)
    assert(p.nicknames === List("a", "b"))
    assert(p.address === Some(Address("Z")))
  }
}

