/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.facebook.buck.distributed;

import com.facebook.buck.rules.coercer.AbstractTypeCoercerFactory;
import com.facebook.buck.rules.coercer.PathTypeCoercer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Type coercers that don't check whether the paths they're coercing actually exist.
 */
public class DistBuildTypeCoercerFactory extends AbstractTypeCoercerFactory {
  public DistBuildTypeCoercerFactory(ObjectMapper mapper) {
    super(mapper, new PathTypeCoercer(PathTypeCoercer.PathExistenceVerificationMode.DO_NOT_VERIFY));
  }
}