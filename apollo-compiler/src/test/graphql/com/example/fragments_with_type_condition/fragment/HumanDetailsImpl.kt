// AUTO-GENERATED FILE. DO NOT MODIFY.
//
// This class was automatically generated by Apollo GraphQL plugin from the GraphQL queries it found.
// It should not be modified by hand.
//
package com.example.fragments_with_type_condition.fragment

import com.apollographql.apollo.api.Fragment
import com.apollographql.apollo.api.Operation
import com.apollographql.apollo.api.internal.ResponseAdapter
import com.example.fragments_with_type_condition.fragment.adapter.HumanDetailsImpl_ResponseAdapter
import kotlin.Double
import kotlin.String

class HumanDetailsImpl : Fragment<HumanDetailsImpl.Data> {
  override fun adapter(): ResponseAdapter<Data> {
    return HumanDetailsImpl_ResponseAdapter
  }

  override fun variables(): Operation.Variables = Operation.EMPTY_VARIABLES

  /**
   * A humanoid creature from the Star Wars universe
   */
  data class Data(
    override val __typename: String = "Human",
    /**
     * What this human calls themselves
     */
    override val name: String,
    /**
     * Height in the preferred unit, default is meters
     */
    override val height: Double?
  ) : HumanDetail, Fragment.Data
}
